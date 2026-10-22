/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.developer

/**
 * Signals that a real implementation was about to run while the corresponding
 * sandbox is active. Callers may catch this specific type and fall back to the
 * local fixture path without hiding unrelated failures.
 */
class SpoofSandboxException(val tool: DevToolId) :
    IllegalStateException("Real ${tool.title} call blocked: sandbox active (local-only)")
