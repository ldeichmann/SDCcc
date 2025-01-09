plugins {
    id("com.draeger.medical.java-conventions")
    id("com.draeger.medical.kotlin-conventions")
    id("com.draeger.medical.executable-conventions")
    id("com.draeger.medical.java-analysis")
    id("com.example.license-report")

    `jvm-test-suite`
}

val javaVersion = property("javaVersion").toString()

tasks.named("build") {
    dependsOn("generateLicenseReport")
}

dependencies {
    api(libs.org.junit.jupiter.junit.jupiter.api)
    api(libs.org.junit.jupiter.junit.jupiter.engine)
    api(libs.org.junit.platform.junit.platform.launcher)
    api(libs.org.junit.platform.junit.platform.reporting)
    api(libs.org.somda.sdc.glue)
    api(libs.org.somda.sdc.common)
    api(libs.commons.cli.commons.cli)

    api(libs.com.google.inject.guice)
    api(libs.com.google.inject.extensions.guice.assistedinject)

    api(libs.org.tomlj.tomlj)

    api(libs.org.apache.logging.log4j.log4j.api)
    api(libs.org.apache.logging.log4j.log4j.core)
    api(libs.org.apache.logging.log4j.log4j.slf4j.impl)

    api(libs.com.github.spotbugs.spotbugs.annotations)
    api(libs.net.sf.saxon.saxon.he)
    api(libs.org.apache.derby.derby)
    api(libs.org.hibernate.hibernate.core)
    api(libs.com.draeger.medical.t2iapi)
    api(libs.jakarta.xml.bind.jakarta.xml.bind.api)
    api(libs.org.glassfish.jaxb.jaxb.core)
    api(libs.org.glassfish.jaxb.jaxb.runtime)
    api(libs.org.bouncycastle.bcprov.jdk15on)
    api(libs.org.bouncycastle.bcpkix.jdk15on)
    api(libs.com.lmax.disruptor)
    api(libs.jakarta.inject.jakarta.inject.api)
    api(libs.org.jetbrains.kotlin.kotlin.reflect)
    api(libs.com.lemonappdev.konsist)
    api(libs.com.google.code.gson.gson)
}

description = "sdccc"

val testsJar by tasks.registering(Jar::class) {
    archiveClassifier.set("tests")
    from(sourceSets["test"].output)
}

//tasks.test {
//    useJUnitPlatform()
//    exclude("it/com/draeger/medical/sdccc/testsuite_it_mock_tests/**")
//    maxHeapSize = "3g"
//    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
//}


testing {
    suites {


        withType(JvmTestSuite::class).matching { it.name in listOf("test", "integrationTest") }.configureEach {
            useJUnitJupiter(libs.org.junit.jupiter.junit.jupiter.engine.get().version!!)

            targets {
                all {
                    testTask.configure {
                        maxHeapSize = "3g"
                        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                    }
                }
            }

            dependencies {
                implementation(project())
                implementation(sourceSets.main.get().output)
                implementation(libs.org.mockito.mockito.core)
                implementation(libs.org.mockito.kotlin.mockito.kotlin)
                implementation(projects.bicepsModel)
                implementation(projects.dpwsModel)
                implementation(libs.com.tngtech.archunit.archunit.junit5)
                implementation(libs.org.junit.jupiter.junit.jupiter.params)
                implementation(libs.org.jetbrains.kotlin.kotlin.test.junit5)

            }
        }

//        val test by getting(JvmTestSuite::class) {
//            useJUnitJupiter(libs.org.junit.jupiter.junit.jupiter.engine.get().version!!)
////            sources {
////                java {
////                    setSrcDirs(listOf("src/test/java/"))
//////                    exclude("it/**")
////                }
////            }
//        }

        val integrationTest by registering(JvmTestSuite::class) {
            testType = TestSuiteType.INTEGRATION_TEST
            dependencies {
                implementation(sourceSets.test.get().output)
            }

            targets {
                all {
                    testTask.configure {
                        filter {
                            exclude("com/draeger/medical/sdccc/testsuite_it_mock_tests/**")
                        }
                    }
                }
            }
//
//            sources {
//                java {
////                    srcDirs(
////                        listOf("src/test/java")
////                    )
////                    exclude("com/**/*Test.java")
////                    include("it/**")
////                    include("com/draeger/medical/sdccc/util/HibernateConfigInMemoryImpl")
//                }
//            }
        }

//            sources {
//                java {
////                    setSrcDirs(listOf("src/test/java/"))
////                    exclude("it/**")
//                }
//            }
//        }

        tasks.named("check") {
            dependsOn(integrationTest)
        }
    }
}

