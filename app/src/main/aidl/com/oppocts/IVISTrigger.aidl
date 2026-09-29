// IVISTrigger.aidl
package com.oppocts;

interface IVISTrigger {
    boolean triggerCTS();
    void startKeyMonitoring(String triggerMethod);
    void stopKeyMonitoring();
    String getDetectedKeys();
    void destroy();
}
