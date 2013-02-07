package io.cloudsoft.brooklyn.stackato.example

import groovy.transform.InheritConstructors
import io.cloudsoft.brooklyn.stackato.StackatoDeployment

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.enricher.basic.SensorTransformingEnricher
import brooklyn.entity.Effector
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.NamedParameter
import brooklyn.entity.basic.StartableApplication
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.AbstractController
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.event.basic.DependentConfiguration
import brooklyn.example.cloudfoundry.MovableCloudFoundryClusterExample
import brooklyn.example.cloudfoundry.MovableElasticWebAppCluster
import brooklyn.extras.cloudfoundry.CloudFoundryJavaWebAppCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.launcher.BrooklynServerDetails
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.util.CommandLineUtil

import com.google.common.base.Joiner
import com.google.common.collect.Lists

@InheritConstructors
class GlobalPortableStackatoExample extends AbstractApplication {

    public static final Logger log = LoggerFactory.getLogger(GlobalPortableStackatoExample.class);
    
	public static Effector<Void> ADD_CLUSTER = new MethodEffector(this.&addCluster);
	
    public static final String WAR_PATH = "classpath://hello-world-webapp.war";
    
    static final List<String> DEFAULT_LOCATIONS = [
            "cloudfoundry:https://api.brooklyn-stackato-rICx1b0I.geopaas.org/",
        ];
    
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()
    
	@Override
	public void postConstruct() {
	    DynamicFabric webFabric = addChild(getEntityManager().createEntity(DynamicFabric, name: "Web Fabric", 
				memberSpec: BasicEntitySpec.newInstance(MovableCloudFoundryClusterExample.class)));
	    
	    StackatoDeployments stackatos = addChild(getEntityManager().createEntity(StackatoDeployments));
	    
	    GeoscalingDnsService geoDns = addChild(getEntityManager().createEntity(GeoscalingDnsService, name: "GeoScaling DNS",
	            username: config.getFirst("brooklyn.geoscaling.username", failIfNone:true),
	            password: config.getFirst("brooklyn.geoscaling.password", failIfNone:true),
	            primaryDomainName: config.getFirst("brooklyn.geoscaling.primaryDomain", failIfNone:true),
	            smartSubdomainName: 'brooklyn-global'));
	
        //specify the WAR file to use
        webFabric.setConfig(ElasticJavaWebAppService.ROOT_WAR, WAR_PATH);
        //load-balancer instances must run on 80 to work with GeoDNS (default is 8000)
        webFabric.setConfig(AbstractController.PROXY_HTTP_PORT, 80);
        //CloudFoundry requires to be told what URL it should listen to, which is chosen by the GeoDNS service
        webFabric.setConfig(CloudFoundryJavaWebAppCluster.HOSTNAME_TO_USE_FOR_URL,
            DependentConfiguration.attributeWhenReady(geoDns, Attributes.HOSTNAME));
		
		// FIXME 
        //keep any demoted web cluster alive until DNS updates 
        webFabric.setConfig(MovableElasticWebAppCluster.TIME_TO_LIVE_SECONDS, geoDns.getTimeToLiveSeconds());
	
        //tell GeoDNS what to monitor
        geoDns.setTargetEntityProvider(webFabric);

        // let the stackatos group use DNS to set up the new instances it creates        
        stackatos.useDnsClient(new GeoscalingWebClient(
            config.getFirst("brooklyn.geoscaling.username", failIfNone:true),
            config.getFirst("brooklyn.geoscaling.password", failIfNone:true) )); 
            
        // and advertise the top level URL
        addEnricher(new SensorTransformingEnricher(geoDns, GeoscalingDnsService.HOSTNAME, WebAppService.ROOT_URL,
            { "http://"+it+"/" } ));
    }

    @Description("Start the web app running in the indicated Stackato cluster")
    public String addCluster(@NamedParameter("location") String location) {
        if (!location.startsWith("cloudfoundry:")) {
            String newClusterId = stackatos.newStackatoDeployment(location);
            log.info("Created Stackato cluster in "+location+", ID "+newClusterId+"; now adding to fabric");
            location = "cloudfoundry"+":"+"https://"+getManagementContext().getEntity(newClusterId).getAttribute(StackatoDeployment.STACKATO_ENDPOINT)+"/";
        }
        log.info("Adding webapp in "+location);
        webFabric.start(new LocationRegistry().getLocationsById([location]));
    }

	public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String locations = CommandLineUtil.getCommandLineOption(args, "--locations", Joiner.on(",").join(DEFAULT_LOCATIONS));

		BrooklynServerDetails server = BrooklynLauncher.newLauncher()
				.webconsolePort(port)
				.launch();

		StartableApplication app = ApplicationBuilder.builder(StartableApplication.class)
				.appImpl(GlobalPortableStackatoExample.class)
				.displayName("Brooklyn Global Stackato Example")
				.manage(server.getManagementContext())
		
        List<Location> locs = server.getManagementContext().getLocationRegistry().resolve([locations]);
		app.start(locs);
		
		Entities.dumpInfo(app);
	}
}
