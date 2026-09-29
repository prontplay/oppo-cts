// IVISTrigger.aidl
package com.ctslauncher;

interface IVISTrigger {
    boolean triggerCTS();
    void startKeyMonitoring(String triggerMethod);
    void stopKeyMonitoring();
    String getDetectedKeys();
    void destroy();
}
