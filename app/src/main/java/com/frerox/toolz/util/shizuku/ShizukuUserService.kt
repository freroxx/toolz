package com.frerox.toolz.util.shizuku

import com.frerox.toolz.IUserService
import com.frerox.toolz.ICommandCallback
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

class ShizukuUserService : IUserService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun runCommand(cmd: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun runCommandWithCallback(cmd: String, callback: ICommandCallback) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            
            val outThread = Thread {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                try {
                    while (reader.readLine().also { line = it } != null) {
                        callback.onOutput(line)
                    }
                } catch (e: Exception) {
                    callback.onError("STDOUT Error: ${e.message}")
                }
            }

            val errThread = Thread {
                val reader = BufferedReader(InputStreamReader(process.errorStream))
                var line: String?
                try {
                    while (reader.readLine().also { line = it } != null) {
                        callback.onError(line)
                    }
                } catch (e: Exception) {
                    callback.onError("STDERR Error: ${e.message}")
                }
            }

            outThread.start()
            errThread.start()

            val exitCode = process.waitFor()
            outThread.join()
            errThread.join()
            
            callback.onExit(exitCode)
        } catch (e: Exception) {
            callback.onError("Exception: ${e.message}")
            callback.onExit(-1)
        }
    }
}
