package com.frerox.toolz;

interface IEngineDebugCallback {
    void onLogReceived(String log);
    void onMotionStatusChanged(String status);
}
