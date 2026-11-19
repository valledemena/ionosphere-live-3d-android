// R4.6.6 - resumen determinista de tests en el log de Gradle.
//
// Motivo: el job `unitTest` del CI solo ejecuta `testDebugUnitTest` y su log no
// publica cuantos tests corrieron. Hasta ahora "324 tests / 0 fallos / 51
// ficheros" era evidencia local, no refrendada por el run.
//
// Este script vive en Gradle y no en `.github/workflows` por dos razones:
//   1. `.github/**` esta fuera de alcance en este repo.
//   2. Asi el resumen sale igual en local y en CI, de la misma fuente: los XML
//      que la propia tarea `Test` acaba de escribir.
//
// La linea es estable y parseable (`clave=valor`), ASCII y sin fecha, para poder
// citarla en un acta y compararla entre runs.

import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

data class ResumenTests(
    val ficheros: Int,
    val tests: Int,
    val fallos: Int,
    val errores: Int,
    val omitidos: Int,
) {
    fun linea(tarea: String): String =
        "RESUMEN-TESTS tarea=$tarea ficheros=$ficheros tests=$tests " +
            "fallos=$fallos errores=$errores omitidos=$omitidos"
}

fun resumenTestsDe(dir: File): ResumenTests {
    val xmls = dir.listFiles { f: File ->
        f.isFile && f.name.startsWith("TEST-") && f.name.endsWith(".xml")
    }?.sortedBy { it.name }.orEmpty()

    var tests = 0
    var fallos = 0
    var errores = 0
    var omitidos = 0

    if (xmls.isNotEmpty()) {
        val factory = DocumentBuilderFactory.newInstance()
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (_: Exception) {
            // Parser sin soporte de la feature: los XML son nuestros y no traen DTD.
        }
        for (f in xmls) {
            val suites = factory.newDocumentBuilder().parse(f).getElementsByTagName("testsuite")
            for (i in 0 until suites.length) {
                val attrs = suites.item(i).attributes
                fun n(nombre: String): Int =
                    attrs.getNamedItem(nombre)?.nodeValue?.toIntOrNull() ?: 0
                tests += n("tests")
                fallos += n("failures")
                errores += n("errors")
                omitidos += n("skipped")
            }
        }
    }

    return ResumenTests(xmls.size, tests, fallos, errores, omitidos)
}

tasks.withType<Test>().configureEach {
    doLast {
        val tarea = this as Test
        val declarada = tarea.reports.junitXml.outputLocation.get().asFile
        val real = if (declarada.isDirectory) declarada
        else project.layout.buildDirectory.dir("test-results/${tarea.name}").get().asFile

        val resumen = resumenTestsDe(real)
        logger.lifecycle(resumen.linea(tarea.name))

        // Sin XML no hay nada que afirmar: fallar es mas honesto que imprimir
        // un cero que pasaria por bueno.
        if (resumen.ficheros == 0) {
            throw GradleException(
                "RESUMEN-TESTS: sin resultados en $real; no se puede afirmar " +
                    "el numero de tests de ${tarea.name}"
            )
        }
    }
}
