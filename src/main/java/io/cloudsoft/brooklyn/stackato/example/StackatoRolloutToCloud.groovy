package io.cloudsoft.brooklyn.stackato.example;

import io.cloudsoft.brooklyn.stackato.StackatoDeployment
import io.cloudsoft.brooklyn.stackato.StackatoDeploymentImpl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.StartableApplication
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient
import brooklyn.launcher.BrooklynLauncher
import brooklyn.launcher.BrooklynServerDetails
import brooklyn.location.Location

import com.google.common.collect.ImmutableList

public class StackatoRolloutToCloud extends AbstractApplication {

    public static final Logger log = LoggerFactory.getLogger(StackatoRolloutToCloud.class);

	static BrooklynProperties config = BrooklynProperties.Factory.newDefault();
	
	@Override
	public void postConstruct() {
	    StackatoDeployment stackato = addChild(getEntityManager().createEntity(StackatoDeployment,
	        cluster: "brooklyn-stackato-"+id,
	        domain: "geopaas.org",
	        admin: "me@fun.net",
	        password: "funfunfun",
	        initialNumDeas: 4,
	        minRam: 4096));

		// optionally use a DNS service to configure our domain name
        stackato.useDnsClient(new GeoscalingWebClient(
            config.getFirst("brooklyn.geoscaling.username", failIfNone:true),
            config.getFirst("brooklyn.geoscaling.password", failIfNone:true) )); 
	}
	
    public static void main(String[] argv) {
        // choose where you want to deploy
        //String location = "jclouds:hpcloud-compute";
		String location = "jclouds:aws-ec2:us-east-1";
		String webConsolePort = "8081+";

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(webConsolePort)
                .launch();

        StartableApplication app = ApplicationBuilder.builder(StartableApplication.class)
				.appImpl(StackatoRolloutToCloud.class)
				.displayName("Stackato")
				.manage(server.getManagementContext())
        
        Location loc = server.getManagementContext().getLocationRegistry().resolve(location);
        app.start(ImmutableList.of(loc));
        
        Entities.dumpInfo(app);
    }
}
