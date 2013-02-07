package io.cloudsoft.brooklyn.stackato;


public interface StackatoDeaNode extends StackatoNode {

    public void blockUntilReadyToLaunch();

}
