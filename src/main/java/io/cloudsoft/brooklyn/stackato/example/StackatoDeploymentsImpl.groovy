package io.cloudsoft.brooklyn.stackato.example


import groovy.transform.InheritConstructors
import io.cloudsoft.brooklyn.stackato.StackatoDeployment
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Entities
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient
import brooklyn.location.Location

@InheritConstructors
public class StackatoDeploymentsImpl extends AbstractEntity implements StackatoDeployments {

    @Override
    public String newStackatoDeployment(String location) {
        StackatoDeployment stackato = addChild(getEntityManager().createEntity(StackatoDeployment,
                domain: "geopaas.org",
                admin: "me@fun.net",
                password: "funfunfun"
                ));
        stackato.setConfig(StackatoDeployment.STACKATO_CLUSTER_NAME, "brooklyn-stackato-"+stackato.id);
        stackato.useDnsClient(dnsClient);
		
        Location loc = getManagementContext().getLocationRegistry().resolve(location);
		if (Entities.isManaged(this)) Entities.manage(stackato);
        stackato.start([loc]);
        return stackato.id;
    }

    private GeoscalingWebClient dnsClient;
    
    // for now Geoscaling is the only one supported; this could be abstracted however, all we need is editSubdomainRecord
	@Override
    public void useDnsClient(GeoscalingWebClient dnsClient) {
        this.dnsClient = dnsClient;
    }

}
