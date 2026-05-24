package com.frerox.toolz;

interface ICommandCallback {
    void onOutput(String line);
    void onError(String line);
    void onExit(int code);
}
