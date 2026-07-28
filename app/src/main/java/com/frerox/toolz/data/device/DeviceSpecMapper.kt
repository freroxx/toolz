package com.frerox.toolz.data.device

/**
 * Sanitizes specification labels and values to handle real-world data quirks.
 */
object DeviceSpecMapper {

    /**
     * Cleans a specification label (key).
     */
    fun sanitizeLabel(label: String?): String {
        if (label == null || label.isBlank()) return "Detail"
        
        return label.replace("\u00A0", " ")
            .trim()
            .removeSuffix(":")
            .replace(Regex("\\s+"), " ")
            .ifBlank { "Detail" }
    }

    /**
     * Cleans a specification value.
     */
    fun sanitizeValue(value: String?): String {
        if (value == null || value.isBlank()) return "Not listed"

        return value
            .replace("\\n", "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .replace("\u00A0", " ")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
            .replace(Regex(" +"), " ")
            .ifBlank { "Not listed" }
    }

    /**
     * Attempts to elevate a low-resolution thumbnail to a high-resolution "bigpic" render.
     */
    fun sanitizeImageUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        
        return url
            .replace("/thumbs/", "/bigpic/")
            .replace("-thumb", "")
            .trim()
    }

    /**
     * Maps the new raw server response to a UI-friendly model.
     */
    fun mapResponse(response: DeviceSpecResponse, isFromCache: Boolean = false): DeviceSpecUiModel {
        val specs = response.specifications
        
        // Extract common quick specs from the map
        val quickSpecs = mutableListOf<QuickSpecItem>()
        
        val display = specs["Display"]
        display?.get("Size")?.let { quickSpecs.add(QuickSpecItem("Display", it)) }
        
        val platform = specs["Platform"]
        platform?.get("Chipset")?.let { quickSpecs.add(QuickSpecItem("Chipset", it)) }
        platform?.get("CPU")?.let { quickSpecs.add(QuickSpecItem("CPU", it)) }
        platform?.get("GPU")?.let { quickSpecs.add(QuickSpecItem("GPU", it)) }

        val memory = specs["Memory"]
        memory?.get("Internal")?.let { quickSpecs.add(QuickSpecItem("Memory", it)) }

        val battery = specs["Battery"]
        battery?.get("Type")?.let { quickSpecs.add(QuickSpecItem("Battery", it)) }

        // Map all categories
        val categories = specs.map { (catName, items) ->
            SpecCategory(
                name = catName.trim(),
                items = items.map { (name, value) ->
                    SpecDetail(
                        name = sanitizeLabel(name),
                        value = sanitizeValue(value)
                    )
                }.filter { it.name != "Detail" || it.value != "Not listed" }
            )
        }.filter { it.items.isNotEmpty() }

        return DeviceSpecUiModel(
            name = response.matchedDevice.ifBlank { "Unknown Device" },
            image = sanitizeImageUrl(response.image.ifBlank { response.img }),
            sourceUrl = response.sourceUrl,
            quickSpecs = quickSpecs,
            categories = categories,
            isFromCache = isFromCache
        )
    }
}
