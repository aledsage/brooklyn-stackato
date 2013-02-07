package io.cloudsoft.brooklyn.stackato.example;


import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient;

public interface StackatoDeployments extends Entity {

    public static Effector<String> NEW_STACKATO_DEPLOYMENT = new MethodEffector<String>(StackatoDeployments.class, "newStackatoDeployment");
    
    @Description("Start a Stackato cluster in the indicated location")
    public String newStackatoDeployment(@NamedParameter("location") String location);
    
    // for now Geoscaling is the only one supported; this could be abstracted however, all we need is editSubdomainRecord
    public void useDnsClient(GeoscalingWebClient dnsClient);
}
