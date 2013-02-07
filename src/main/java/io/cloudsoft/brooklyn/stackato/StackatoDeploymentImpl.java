package io.cloudsoft.brooklyn.stackato;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.Location;
import brooklyn.util.MutableMap;
import brooklyn.util.ShellUtils;
import brooklyn.util.flags.SetFromFlag;

public class StackatoDeploymentImpl extends AbstractEntity implements StackatoDeployment {

    public static final Logger log = LoggerFactory.getLogger(StackatoDeployment.class);

    static { RendererHints.register(StackatoDeployment.STACKATO_MGMT_CONSOLE_URL, new RendererHints.NamedActionWithUrl("Open")); }
    
    private StackatoMasterNode master;
    
    private GeoscalingWebClient dnsClient;
    
    @SetFromFlag("skipDeaCluster")
    private boolean skipDeaCluster;

    public StackatoDeploymentImpl() {
	}

    public StackatoDeploymentImpl(Entity parent) {
    	this(new MutableMap(), parent);
	}
    
    public StackatoDeploymentImpl(Map flags, Entity parent) { 
        super(flags, parent);
    }
    
    @Override
    public void postConstruct() {
    	super.postConstruct();
    	
        addMaster();
        if (skipDeaCluster!=Boolean.TRUE) addDeaCluster();
    }
    
    public void addMaster() {
        if (master!=null) return;
        master = getEntityManager().createEntity(BasicEntitySpec.newInstance(StackatoMasterNode.class).parent(this));
        addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(master, MASTER_INTERNAL_IP, StackatoMasterNode.MASTER_UP));
        if (Entities.isManaged(this)) Entities.manage(master);
    }

    public void addDeaCluster() {
        if (master==null)
            log.warn("DEA cluster being added to "+this+" but no master is configured; may not start unless sensors are wired in correclty");
        
        DynamicCluster deaCluster = getEntityManager().createEntity(BasicEntitySpec.newInstance(DynamicCluster.class)
        		.configure(DynamicCluster.MEMBER_SPEC, BasicEntitySpec.newInstance(StackatoDeaNode.class))
        		.configure(DynamicCluster.INITIAL_SIZE, getConfig(INITIAL_SIZE))
        		.parent(this));
        if (Entities.isManaged(this)) Entities.manage(master);
        
        log.info("created descriptor for "+this+", containing master "+master+" and deaCluster "+deaCluster);
    }
    
    // for now Geoscaling is the only one supported; this could be abstracted however, all we need is editSubdomainRecord
    public void useDnsClient(GeoscalingWebClient dnsClient) {
        this.dnsClient = dnsClient;
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        StartableMethods.start(this, locations);

        String ip = master.getAttribute(StackatoMasterNode.ADDRESS);
        String endpoint = master.getAttribute(StackatoMasterNode.STACKATO_ENDPOINT);
        String cluster = getConfig(StackatoDeployment.STACKATO_CLUSTER_NAME);
        String url = "https://"+master.getAttribute(StackatoMasterNode.STACKATO_ENDPOINT);
        String domain = getConfig(StackatoDeployment.STACKATO_CLUSTER_DOMAIN);

        log.info("Setting up DNS and accounts for Stackato at "+endpoint);
        boolean setupStackatoDns = setupStackatoDns(ip, endpoint, cluster, domain);
        boolean setupLicensedAdminUser = setupLicensedAdminUser(endpoint);        
        boolean setupLocalCliAccounts = setupStackatoDns && setupLocalCliAccounts(endpoint);
        
        setAttribute(MASTER_PUBLIC_IP, ip);
        setAttribute(STACKATO_ENDPOINT, endpoint);
        setAttribute(STACKATO_MGMT_CONSOLE_URL, url);
        
        // and a welcome / next steps message
        log.info("Stackato setup complete, running at "+ip+" listening on endpoint "+endpoint+"\n"+
    "**************************************************************************\n"+
(setupStackatoDns ? "" :               
    "* Set up following DNS records\n"+
    "* for "+domain+":\n"+
    "*        "+cluster+"   A     "+ip+"\n"+
    "*        *."+cluster+" CNAME "+cluster+"."+domain+"\n"+
    "**************************************************************************\n") +
(setupLicensedAdminUser ? "" :
    "* Login (creating the admin user)\n"+
    "* at:\n" + 
    "*        "+url+"\n"+
    "**************************************************************************\n") +
(setupLocalCliAccounts ? "" : 
    "* Set up local credentials\n"+
    "* with:\n"+
    "*        stackato target "+endpoint+"\n"+
    "*        stackato login\n"+
    "*        vmc target "+endpoint+"\n"+
    "*        vmc login\n"+
    "**************************************************************************\n") +
(setupStackatoDns && setupLicensedAdminUser && setupLocalCliAccounts ? 
    // none of the above apply
    "* Stackato ready (no manual configuration needed)\n"+
    "* at:\n"+
    "*        "+endpoint+"\n"+ 
    "**************************************************************************"
    : ""));
    }
    
    private boolean setupLocalCliAccounts(String endpoint) {
        boolean localLoginDone = false;
        try {
            String username = master.getConfig(STACKATO_ADMIN_USER_EMAIL);
            String password = master.getConfig(STACKATO_PASSWORD);
            // need to target https URL also (default is http)
            ShellUtils.exec("vmc target https://"+endpoint+"/", log, this);
            ShellUtils.exec("echo "+password+" | vmc login "+username, log, this);
            try {
                ShellUtils.exec("vmc target "+endpoint, log, this);
                ShellUtils.exec("echo "+password+" | vmc login "+username, log, this);
            } catch (Exception e) {
                log.info("Command-line vmc access to "+endpoint+" using http could not be configured locally (https worked; likely it is required on server)");
            }
            try {
                ShellUtils.exec("stackato target https://"+endpoint+"/", log, this);
                ShellUtils.exec("echo "+password+" | stackato login "+username, log, this);                
                try {
                    ShellUtils.exec("stackato target "+endpoint, log, this);
                    ShellUtils.exec("echo "+password+" | stackato login "+username, log, this);                
                } catch (Exception e) {
                    log.info("Command-line Stackato access to "+endpoint+" using http could not be configured locally (https worked; likely it is required on server)");
                }
            } catch (Exception e) {
                log.warn("Command-line Stackato access to "+endpoint+" could not be configured locally; ensure `stackato` installed");
            }
            localLoginDone = true;
        } catch (Exception e) {
            // will throw if any problems
            log.warn("Brooklyn Stackato access to "+endpoint+" (using vmc) could not be configured locally; ensure `vmc` installed");
            log.debug("Reason for failed local configuration: "+e, e);            
        }
        return localLoginDone;
    }
    
    private boolean setupStackatoDns(String ip, String endpoint, String cluster, String domain) {
        boolean dnsDone = false;
        try {
            if (dnsClient!=null) {
                Domain gd = dnsClient.getPrimaryDomain(domain);
                if (gd!=null) {
                    gd.editRecord(cluster, "A", ip);
                    gd.editRecord("*."+cluster, "CNAME", cluster+"."+domain);
                    dnsDone = true;
                    log.debug("set up DNS for "+cluster+"."+domain+" using "+dnsClient+", for "+this);
                } else {
                    log.debug("no domain "+domain+" found at "+dnsClient+"; not setting up DNS for "+this);
                }
            }
        } catch (Exception e) {
            //won't normally throw
            log.warn("Failed to set up DNS for "+endpoint+": "+e, e);
        }
        return dnsDone;
    }
    
    private boolean setupLicensedAdminUser(String endpoint) {
        boolean userDone = false;
        try {
            master.addLicensedUser();
            userDone = true;
        } catch (Exception e) {
            // will throw if any problems
            log.warn("Stackato user at "+endpoint+" could not be automatically created (consult logs for more info)");
            log.debug("Reason for failed user creation: "+e, e);
        }
        return userDone;
    }
    
    @Override
    public void stop() {
        StartableMethods.stop(this);
    }
    @Override
    public void restart() {
        StartableMethods.restart(this);
    }
    
}
