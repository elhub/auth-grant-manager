package no.elhub.auth.grantmanager.presentation.utils

import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.io.ObjectInputFilter.Config

fun loadConfig(): Map<String, Any> {
    val inputStream: InputStream? = Config::class.java.getResourceAsStream("/openapi.yaml")
    val yaml = Yaml()
    return yaml.load(inputStream)
}
