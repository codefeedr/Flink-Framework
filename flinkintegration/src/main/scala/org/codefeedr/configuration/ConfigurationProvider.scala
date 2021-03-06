package org.codefeedr.configuration

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.scala.ExecutionEnvironment
import org.codefeedr.util.OptionExtensions.DebuggableOption

import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
  * This component handles the global configuration
  */
trait ConfigurationProvider extends Serializable {

  /**
    * Sets the configuration, both parameters and executionconfig
    *
    * @param configuration custom configuration to initialize with
    */
  def initConfiguration(configuration: ParameterTool, ec: ExecutionConfig): Unit

  /**
    * Initializes just the parameters
    * You need to call initEc too
    * @param arguments
    */
  def initParameters(arguments: ParameterTool)

  /**
    * Initializes just the execution config
    * You still need to initialize the arguments
    * @param ec
    */
  def initEc(ec: ExecutionConfig): Unit

  /**
    * Retrieve the parameterTool for the global configuration
    *
    * @return
    */
  def parameterTool: ParameterTool

  /**
    * Tries to key the value for the given key from the parametertool
    *
    * @param key key value to search for
    * @return value if the key exists in the parameter tool, Otherwise None
    */
  def tryGet(key: String): Option[String]

  /**
    * Returns the value of the given key
    * If no default value is passed and the key does not exist, an exception is thrown
    *
    * @param key     key to look for in the configuration
    * @param default default value
    * @return
    */
  def get(key: String, default: Option[String] = None): String

  /**
    * Returns the value of the given key
    * If no default value is passed and the key does not exist, an exception is thrown
    *
    * @param key     key to look for in the configuration
    * @param default default value
    * @return
    */
  def getInt(key: String, default: Option[Int] = None): Int =
    get(key, default.map(o => o.toString)).toInt

  /**
    * Retrieve the executionConfig used for the current job
    * @return the Flink executionconfig object
    */
  def getExecutionConfig: ExecutionConfig

  def getDefaultAwaitDuration: Duration
}

trait ConfigurationProviderComponent {
  val configurationProvider: ConfigurationProvider
  @transient implicit lazy val ec: ExecutionConfig = configurationProvider.getExecutionConfig
}

trait FlinkConfigurationProviderComponent extends ConfigurationProviderComponent {
  val configurationProvider: ConfigurationProvider
  class ConfigurationProviderImpl extends ConfigurationProvider with LazyLogging {
    //All these variables are transient, because the configuration provider should
    // re-initialize after deserialization, and use the configuration from the execution environment
    @transient
    @volatile private var requested = false
    @volatile private var _parameterTool: Option[ParameterTool] = None
    @transient lazy val parameterTool: ParameterTool = getParameterTool
    private var executionConfig: Option[ExecutionConfig] = None

    def initConfiguration(arguments: ParameterTool, ec: ExecutionConfig): Unit = {
      initParameters(arguments)
      initEc(ec)
    }

    /**
      * Sets the configuration
      * If the passed parameterTool contains a property for "propertiesFile", this path is opened and
      * @param arguments custom configuration to initialize with. Usually initialized from program arguments
      */
    def initParameters(arguments: ParameterTool): Unit = {
      if (requested) {
        //Just to validate, the configuration is not modified after it has already been retrieved by some component
        throw new IllegalStateException(
          "Cannot set parametertool after parametertool was already requested")
      }

      //Store parameter tool in the static context
      //Needed to also make the components work when there is no stream execution environment
      val defaultConfiguraiton = loadPropertiesFile("/codefeedr.properties") match {
        case Some(p) => p
        case None =>
          throw new IllegalArgumentException(
            "No codefeedr.properties found. Did assemly happen properly")
      }

      val propertiesFile = Option(
        defaultConfiguraiton.mergeWith(arguments).get("propertiesFile", null))

      logger.info("Initialized parameter tool")
      _parameterTool = propertiesFile.flatMap(loadPropertiesFile) match {
        case Some(p) => Some(p.mergeWith(defaultConfiguraiton.mergeWith(p).mergeWith(arguments)))
        case None => Some(defaultConfiguraiton.mergeWith(arguments))
      }
    }

    def initEc(ec: ExecutionConfig): Unit = {
      executionConfig = Some(ec)
    }

    override def getExecutionConfig: ExecutionConfig = executionConfig match {
      case Some(ec) => ec
      case None =>
        throw new IllegalStateException("Cannot retrieve executionConfig before being initialized")
    }

    /**
      * Attempts to load the codefeedr.properties file
      * @param fileName Name of the properties file to load
      * @return A paremetertool if the file was found, none otherwise
      */
    private def loadPropertiesFile(fileName: String): Option[ParameterTool] =
      Option(getClass.getResourceAsStream(fileName))
        .info(s"Loading $fileName file", s"No $fileName resource file found.")
        .map(ParameterTool.fromPropertiesFile)

    /**
      * Reads the parameterTool from the codefeedr.properties file, execution environment, or the explicitly initialized parametertool
      * Makes sure that if the properties file was used, the properties are also registered as jobParameters
      *
      * @return
      */
    private def getParameterTool: ParameterTool =
      _parameterTool match {
        case Some(p) => p
        case None =>
          throw new IllegalArgumentException(
            "Cannot retrieve parametertool before calling initialize. Make sure to call initConfiguration as soon as possible in your application, and make sure this ConfigurationProviderComponent gets serialized with the Flink job")
      }

    /**
      * Tries to key the value for the given key from the parametertool
      *
      * @param key key value to search for
      * @return value if the key exists in the parameter tool, Otherwise None
      */
    def tryGet(key: String): Option[String] = {
      if (parameterTool.has(key)) {
        Some(parameterTool.get(key))
      } else {
        None
      }
    }

    /**
      * @param key key to look for in the configuration
      * @param default default value
      * @return
      */
    override def get(key: String, default: Option[String]): String = {
      tryGet(key) match {
        case None =>
          default match {
            case None =>
              throw new IllegalArgumentException(
                s"Cannot find a configuration for $key, and no default value was passed")
            case Some(v) => v
          }
        case Some(v) => v
      }
    }

    @transient override lazy val getDefaultAwaitDuration: FiniteDuration = {
      val seconds = parameterTool.getInt("codefeedr.awaitDuration")
      logger.info(s"Initializing with default await duration $seconds")
      Duration(seconds, SECONDS)
    }

  }
}
