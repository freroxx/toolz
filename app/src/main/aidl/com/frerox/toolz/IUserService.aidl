package com.frerox.toolz;

import com.frerox.toolz.ICommandCallback;

interface IUserService {
    void runCommand(String cmd) = 1;
    void runCommandWithCallback(String cmd, ICommandCallback callback) = 2;
    String getClipboardText() = 3;
    void destroy() = 16777114;
}
