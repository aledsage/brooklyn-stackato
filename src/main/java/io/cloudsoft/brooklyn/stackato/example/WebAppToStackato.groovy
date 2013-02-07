package io.cloudsoft.brooklyn.stackato.example

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.basic.AbstractCombiningEnricher
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.StartableApplication
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.extras.cloudfoundry.CloudFoundryJavaWebAppCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.launcher.BrooklynServerDetails
import brooklyn.location.Location
import brooklyn.policy.Enricher
import brooklyn.policy.autoscaling.AutoScalerPolicy

import com.google.common.collect.Iterables

public class WebAppToStackato extends AbstractApplication {

    public static final Logger log = LoggerFactory.getLogger(WebAppToStackato.class);
    
    public static final String ENDPOINT = "https://api.brooklyn-stackato-rICx1b0I.geopaas.org/";
    
    public static final String WAR_FILE_URL = "classpath://hello-world-webapp.war";

    ElasticJavaWebAppService webApp = null;
    
    public void start(Collection<? extends Location> locations) {
        start(Iterables.getOnlyElement(locations));
        super.start(locations);
        addPolicies();
    }
    public void start(Location location) {
        assert webApp == null;
        webApp = new ElasticJavaWebAppService.Factory().
            newFactoryForLocation(location).
            newEntity(this, war: WAR_FILE_URL);
    }
    
    private void addPolicies() {
        if (webApp in CloudFoundryJavaWebAppCluster) {
            CloudFoundryJavaWebAppCluster stackatoWebApp = webApp;
            
            AttributeSensor<Double> OUR_FUNCTION = new BasicAttributeSensor<Double>(Double, "stackato.usage");
            
            Enricher ourFunction = new AbstractCombiningEnricher(OUR_FUNCTION) {
                double cpu, memory;
                { 
                    subscribe("memory", CloudFoundryJavaWebAppCluster.MEMORY_USED_FRACTION);
                    subscribe("cpu", CloudFoundryJavaWebAppCluster.CPU_USAGE);
                }
                public Double compute() { Math.max(cpu, memory) }
            };
        
            AutoScalerPolicy resizer = AutoScalerPolicy.builder()
					.metric(OUR_FUNCTION)
					.metricRange(0.1, 0.75)
					.sizeRange(1, 4);
            
            stackatoWebApp.addEnricher(ourFunction);
            stackatoWebApp.addPolicy(resizer);
        }
    }

	public static void main(String[] argv) {
		BrooklynServerDetails server = BrooklynLauncher.newLauncher()
				.webconsolePort("8081+")
				.launch();

		StartableApplication app = ApplicationBuilder.builder(StartableApplication.class)
				.appImpl(WebAppToStackato.class)
				.displayName("Stackato")
				.manage(server.getManagementContext())
		
        Location loc = server.getManagementContext().getLocationRegistry().resolve("cloudfoundry:"+ENDPOINT);
		app.start([loc]);
		
		Entities.dumpInfo(app);
	}
}
