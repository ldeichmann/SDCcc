package it.com.draeger.medical.sdccc

import com.draeger.medical.sdccc.TestSuite
import com.draeger.medical.sdccc.configuration.DefaultEnabledTestConfig
import com.draeger.medical.sdccc.configuration.DefaultTestSuiteConfig
import com.draeger.medical.sdccc.configuration.DefaultTestSuiteModule
import com.draeger.medical.sdccc.configuration.TestRunConfig
import com.draeger.medical.sdccc.configuration.TestSuiteConfig
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionException
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionRegistry
import com.draeger.medical.sdccc.messages.HibernateConfig
import com.draeger.medical.sdccc.sdcri.testclient.TestClient
import com.draeger.medical.sdccc.sdcri.testprovider.TestProvider
import com.draeger.medical.sdccc.sdcri.testprovider.TestProviderImpl
import com.draeger.medical.sdccc.sdcri.testprovider.guice.ProviderFactory
import com.draeger.medical.sdccc.tests.InjectorTestBase
import com.draeger.medical.sdccc.util.HibernateConfigInMemoryImpl
import com.draeger.medical.sdccc.util.TestRunObserver
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Singleton
import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.util.Modules
import it.com.draeger.medical.sdccc.test_util.SslMetadata
import it.com.draeger.medical.sdccc.testsuite_it_mock_tests.Identifiers
import it.com.draeger.medical.sdccc.testsuite_it_mock_tests.WasRunObserver
import org.apache.commons.lang3.ArrayUtils
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.DefaultConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito
import org.somda.sdc.biceps.common.storage.PreprocessingException
import org.somda.sdc.common.guice.AbstractConfigurationModule
import org.somda.sdc.dpws.DpwsConfig
import org.somda.sdc.dpws.crypto.CryptoSettings
import org.somda.sdc.dpws.factory.TransportBindingFactory
import org.somda.sdc.dpws.soap.SoapUtil
import org.somda.sdc.dpws.soap.factory.RequestResponseClientFactory
import org.somda.sdc.dpws.soap.wseventing.WsEventingConstants
import org.somda.sdc.dpws.soap.wseventing.model.ObjectFactory
import org.somda.sdc.glue.GlueConstants
import org.somda.sdc.glue.common.CommonConstants
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeoutException
import javax.net.ssl.HttpsURLConnection

class TestSuiteIT2 {

    companion object {
        private val LOG: Logger = LogManager.getLogger()
        private const val TEST_TIMEOUT: Long = 120
        private val SSL_METADATA: SslMetadata = SslMetadata()
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(20)

        init {
            Configurator.reconfigure(DefaultConfiguration())
            Configurator.setRootLevel(Level.INFO)

            SSL_METADATA.startAsync().awaitRunning()
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        }
    }

    val dutEpr: String = "urn:uuid:" + UUID.randomUUID()
    private var testProvider: TestProvider? = null

    private fun createInjector(vararg override: AbstractModule): Injector {
        return Guice.createInjector(
            Modules.override(
                DefaultTestSuiteModule(), DefaultTestSuiteConfig(), DefaultEnabledTestConfig()
            )
                .with(*override)
        )
    }

    @Throws(IOException::class)
    private fun createTestSuiteITInjector(
        cryptoSettings: CryptoSettings,
        failingTests: Boolean,
        locationConfig: LocationConfig?,
        vararg override: AbstractModule
    ): Injector {
        val tempDir = Files.createTempDirectory("SDCccIT_TestSuiteIT")
        tempDir.toFile().deleteOnExit()

        LOG.info("Creating injector for epr {}", dutEpr)

        return createInjector(
            *ArrayUtils.addAll(
                override, MockConfiguration(cryptoSettings, tempDir, failingTests, locationConfig)
            )
        )
    }

    @Throws(IOException::class)
    private fun getProvider(): TestProvider {
        val providerCert = checkNotNull(SSL_METADATA.serverKeySet)
        val providerCrypto = SslMetadata.getCryptoSettings(providerCert)

        val injector = createTestSuiteITInjector(providerCrypto, false, null)

        TestSuiteIT::class.java.getResourceAsStream("TestSuiteIT/mdib.xml").use { mdibAsStream ->
            Assertions.assertNotNull(mdibAsStream)
            val providerFac =
                injector.getInstance(
                    ProviderFactory::class.java
                )
            return providerFac.createProvider(mdibAsStream!!)
        }
    }

    @Throws(IOException::class)
    private fun getConsumerInjector(
        failingTests: Boolean, locationConfig: LocationConfig?, vararg override: AbstractModule
    ): Injector {
        val consumerCert = checkNotNull(SSL_METADATA.clientKeySet)
        val consumerCrypto = SslMetadata.getCryptoSettings(consumerCert)

        return createTestSuiteITInjector(consumerCrypto, failingTests, locationConfig, *override)
    }

    @BeforeEach
    @Throws(IOException::class)
    fun setUp() {
        this.testProvider = getProvider()

        val discoveryAccess =
            testProvider!!.sdcDevice.device.discoveryAccess
        discoveryAccess.setTypes(listOf(CommonConstants.MEDICAL_DEVICE_TYPE))
        discoveryAccess.setScopes(listOf(GlueConstants.SCOPE_SDC_PROVIDER))
    }

    @AfterEach
    @Throws(TimeoutException::class)
    fun tearDown() {
        testProvider!!.stopService(DEFAULT_TIMEOUT)
    }

    /**
     * Runs the test suite with a mock client and mock tests, expected to pass.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @Throws(
        IOException::class,
        PreprocessingException::class,
        TimeoutException::class
    )
    fun testConsumer() {
        testProvider!!.startService(DEFAULT_TIMEOUT)

        val injector = getConsumerInjector(false, null)

        val injectorSpy = Mockito.spy(injector)
        val testClientSpy = Mockito.spy(injector.getInstance(TestClient::class.java))
        Mockito.`when`(injectorSpy.getInstance(TestClient::class.java)).thenReturn(testClientSpy)

        InjectorTestBase.setInjector(injectorSpy)

        val obs = injector.getInstance(WasRunObserver::class.java)
        Assertions.assertFalse(obs.hadDirectRun())
        Assertions.assertFalse(obs.hadInvariantRun())

        val testSuite = injector.getInstance(TestSuite::class.java)

        val run = testSuite.runTestSuite()
        Assertions.assertEquals(0, run, "SDCcc had an unexpected failure")

        Assertions.assertTrue(obs.hadDirectRun())
        Assertions.assertTrue(obs.hadInvariantRun())

        // no invalidation is allowed in the test run
        val testRunObserver = injector.getInstance(TestRunObserver::class.java)
        Assertions.assertFalse(
            testRunObserver.isInvalid,
            "TestRunObserver had unexpected failures: " + testRunObserver.reasons
        )
    }

    /**
     * Runs the test suite with a mock client, mock tests and preconditions throwing an exception, expected to fail, but
     * not abort on phase 3.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @Throws(
        IOException::class,
        PreconditionException::class,
        PreprocessingException::class,
        TimeoutException::class
    )
    fun testInvalid() {
        testProvider!!.startService(DEFAULT_TIMEOUT)

        val preconditionRegistryMock = Mockito.mock(PreconditionRegistry::class.java)
        Mockito.doThrow(NullPointerException("intentional exception for testing purposes"))
            .`when`(preconditionRegistryMock)
            .runPreconditions()

        val injector = getConsumerInjector(false, null, object : AbstractModule() {
            /**
             * Configures a [com.google.inject.Binder] via the exposed methods.
             */
            override fun configure() {
                super.configure()
                bind(PreconditionRegistry::class.java).toInstance(preconditionRegistryMock)
            }
        })

        InjectorTestBase.setInjector(injector)

        val obs = injector.getInstance(WasRunObserver::class.java)
        Assertions.assertFalse(obs.hadDirectRun())
        Assertions.assertFalse(obs.hadInvariantRun())

        val testSuite = injector.getInstance(TestSuite::class.java)

        val run = testSuite.runTestSuite()
        Assertions.assertEquals(0, run, "SDCcc had an unexpected failure")

        Assertions.assertTrue(obs.hadDirectRun())
        Assertions.assertTrue(obs.hadInvariantRun())

        Mockito.verify(preconditionRegistryMock, Mockito.atLeastOnce()).runPreconditions()

        // no invalidation is allowed in the test run
        val testRunObserver = injector.getInstance(TestRunObserver::class.java)
        Assertions.assertTrue(testRunObserver.isInvalid, "TestRunObserver did not have a failure")
    }

    /**
     * Runs the test consumer and causes a failed renewal, verifies that test run is marked invalid.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @Throws(Exception::class)
    fun testConsumerUnexpectedSubscriptionEnd() {
        testProvider!!.startService(DEFAULT_TIMEOUT)

        val injector = getConsumerInjector(false, null)
        InjectorTestBase.setInjector(injector)

        val obs = injector.getInstance(WasRunObserver::class.java)
        Assertions.assertFalse(obs.hadDirectRun())
        Assertions.assertFalse(obs.hadInvariantRun())

        val client = injector.getInstance(TestClient::class.java)
        client.startService(DEFAULT_TIMEOUT)
        client.connect()

        val soapUtil = client.injector.getInstance(SoapUtil::class.java)
        val wseFactory = client.injector.getInstance(
            ObjectFactory::class.java
        )

        // get active subscription id
        val activeSubs = testProvider!!.activeSubscriptions
        Assertions.assertEquals(1, activeSubs.size, "Expected only one active subscription")

        val subMan = activeSubs.values.stream().findFirst().orElseThrow()

        val subManAddress = subMan.subscriptionManagerEpr.address

        // unsubscribe from outside the client, next renew should mark test run invalid
        val transportBindingFactory = client.injector.getInstance(
            TransportBindingFactory::class.java
        )
        val transportBinding = transportBindingFactory.createHttpBinding(subManAddress.value, null)

        val rrClientFactory = client.injector.getInstance(
            RequestResponseClientFactory::class.java
        )
        val requestResponseClient = rrClientFactory.createRequestResponseClient(transportBinding)

        val unsubscribe =
            soapUtil.createMessage(WsEventingConstants.WSA_ACTION_UNSUBSCRIBE, wseFactory.createUnsubscribe())
        unsubscribe.wsAddressingHeader.setTo(subManAddress)

        LOG.info("Unsubscribing for address {}", subManAddress.value)
        val response = requestResponseClient.sendRequestResponse(unsubscribe)
        Assertions.assertFalse(response.isFault, "unsubscribe faulted")

        // wait until subscription must've ended and renews must've failed
        val subscriptionEnd = Duration.between(Instant.now(), subMan.expiresTimeout)

        if (!subscriptionEnd.isNegative) {
            Thread.sleep(subscriptionEnd.toMillis())
        }

        // dead subscription must've been marked
        val testRunObserver = injector.getInstance(TestRunObserver::class.java)
        Assertions.assertTrue(testRunObserver.isInvalid, "TestRunObserver had unexpectedly absent failures")
    }

    /**
     * Runs the test consumer, connects and disconnects. Test runs should not be marked invalid.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @Throws(Exception::class)
    fun testConsumerExpectedDisconnect() {
        testProvider!!.startService(DEFAULT_TIMEOUT)

        val injector = getConsumerInjector(false, null)
        InjectorTestBase.setInjector(injector)

        val obs = injector.getInstance(WasRunObserver::class.java)
        Assertions.assertFalse(obs.hadDirectRun())
        Assertions.assertFalse(obs.hadInvariantRun())

        val client = injector.getInstance(TestClient::class.java)
        client.startService(DEFAULT_TIMEOUT)
        client.connect()

        // get active subscription id
        val activeSubs = testProvider!!.activeSubscriptions
        Assertions.assertEquals(1, activeSubs.size, "Expected only one active subscription")

        val subManTimeout =
            activeSubs.values.stream().findFirst().orElseThrow().expiresTimeout

        client.disconnect()

        // wait until subscription must've ended
        val subscriptionEnd = Duration.between(Instant.now(), subManTimeout)

        if (!subscriptionEnd.isNegative) {
            Thread.sleep(subscriptionEnd.toMillis())
        }

        // test run should not be marked invalid, as disconnect was intentional
        val testRunObserver = injector.getInstance(TestRunObserver::class.java)
        Assertions.assertFalse(
            testRunObserver.isInvalid,
            "TestRunObserver had unexpected failures: " + testRunObserver.reasons
        )
    }

    /**
     * Test failures are counted for invariant and direct tests with a mock client and mock tests.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @Throws(
        IOException::class,
        PreprocessingException::class,
        TimeoutException::class
    )
    fun testMockConsumerFailures() {
        testProvider!!.startService(DEFAULT_TIMEOUT)

        val injector = getConsumerInjector(true, null)
        InjectorTestBase.setInjector(injector)

        val obs = injector.getInstance(WasRunObserver::class.java)
        Assertions.assertFalse(obs.hadDirectRun())
        Assertions.assertFalse(obs.hadInvariantRun())

        val testSuite = injector.getInstance(TestSuite::class.java)

        val run = testSuite.runTestSuite()
        Assertions.assertEquals(2, run, "SDCcc had an unexpected amount of failures")

        Assertions.assertTrue(obs.hadDirectRun())
        Assertions.assertTrue(obs.hadInvariantRun())

        // no invalidation is allowed in the test run
        val testRunObserver = injector.getInstance(TestRunObserver::class.java)
        Assertions.assertFalse(
            testRunObserver.isInvalid,
            "TestRunObserver had unexpected failures: " + testRunObserver.reasons
        )
    }

    private data class LocationConfig(
        val facility: String?,
        val building: String?,
        val pointOfCare: String?,
        val floor: String?,
        val room: String?,
        val bed: String?
    )

    private inner class MockConfiguration(
        private val cryptoSettings: CryptoSettings,
        private val tempDir: Path,
        private val failingTests: Boolean,
        locationConfig: LocationConfig?
    ) :
        AbstractConfigurationModule() {
        private val locationConfig: LocationConfig = Objects.requireNonNullElseGet(
            locationConfig
        ) {
            LocationConfig(
                null,
                null,
                null,
                null,
                null,
                null
            )
        }

        override fun defaultConfigure() {
            bind(CryptoSettings::class.java).toInstance(cryptoSettings)

            bind(TestSuiteConfig.CI_MODE, Boolean::class.java, true)
            bind(TestSuiteConfig.CONSUMER_ENABLE, Boolean::class.java, true)
            bind(TestSuiteConfig.CONSUMER_DEVICE_EPR, String::class.java, dutEpr)
            bind(
                TestSuiteConfig.CONSUMER_DEVICE_LOCATION_FACILITY,
                String::class.java, locationConfig.facility
            )
            bind(
                TestSuiteConfig.CONSUMER_DEVICE_LOCATION_BUILDING,
                String::class.java, locationConfig.building
            )
            bind(
                TestSuiteConfig.CONSUMER_DEVICE_LOCATION_POINT_OF_CARE,
                String::class.java, locationConfig.pointOfCare
            )
            bind(
                TestSuiteConfig.CONSUMER_DEVICE_LOCATION_FLOOR,
                String::class.java, locationConfig.floor
            )
            bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_ROOM, String::class.java, locationConfig.room)
            bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_BED, String::class.java, locationConfig.bed)

            bind(TestSuiteConfig.PROVIDER_ENABLE, Boolean::class.java, true)
            bind(TestSuiteConfig.PROVIDER_DEVICE_EPR, String::class.java, dutEpr)

            bind(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS, String::class.java, "127.0.0.1")
            bind(DpwsConfig.MULTICAST_TTL, Int::class.java, 128)

            bind(HibernateConfig::class.java).to(HibernateConfigInMemoryImpl::class.java).`in`(Singleton::class.java)
            install(
                FactoryModuleBuilder()
                    .implement(TestProvider::class.java, TestProviderImpl::class.java)
                    .build(ProviderFactory::class.java)
            )

            bind(
                TestSuiteConfig.SDC_TEST_DIRECTORIES,
                Array<String>::class.java, arrayOf(
                    "it.com.draeger.medical.sdccc.testsuite_it_mock_tests",
                )
            )

            bind(TestRunConfig.TEST_RUN_DIR, File::class.java, tempDir.toFile())

            // enable the mock tests
            bind(Identifiers.DIRECT_TEST_IDENTIFIER, Boolean::class.java, true)
            bind(Identifiers.INVARIANT_TEST_IDENTIFIER, Boolean::class.java, true)

            bind(Identifiers.DIRECT_TEST_IDENTIFIER_FAILING, Boolean::class.java, failingTests)
            bind(Identifiers.INVARIANT_TEST_IDENTIFIER_FAILING, Boolean::class.java, failingTests)
        }
    }

}