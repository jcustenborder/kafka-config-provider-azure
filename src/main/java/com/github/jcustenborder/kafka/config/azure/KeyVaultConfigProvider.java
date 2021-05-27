/**
 * Copyright © 2021 Jeremy Custenborder (jcustenborder@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jcustenborder.kafka.config.azure;

import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jcustenborder.kafka.connect.utils.config.Description;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationSection;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationSections;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationTip;
import com.google.common.collect.ImmutableSet;
import org.apache.kafka.common.config.ConfigData;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.config.provider.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Description("This config provider is used to retrieve secrets from the Microsoft Azure Key Vault service.")
@DocumentationTip("Config providers can be used with anything that supports the AbstractConfig base class that is shipped with Apache Kafka.")
@DocumentationSections(
    sections = {
        @DocumentationSection(title = "Secret Value", text = "The value for the secret must be formatted as a JSON object. " +
            "This allows multiple keys of data to be stored in a single secret. The name of the secret in Microsoft Azure Key Vault " +
            "will correspond to the path that is requested by the config provider.\n" +
            "\n" +
            ".. code-block:: json\n" +
            "    :caption: Example Secret Value\n" +
            "\n" +
            "    {\n" +
            "      \"username\" : \"db101\",\n" +
            "      \"password\" : \"superSecretPassword\"\n" +
            "    }\n" +
            ""),
        @DocumentationSection(title = "Secret Retrieval", text = "The ConfigProvider will use the name of the secret to build the request to the Key Vault service. " +
            "This behavior can be overridden by setting `config.providers.keyVault.param.prefix=staging-` and requested the secret with `${keyVault:test-secret}`, " +
            "the ConfigProvider will build a request for `staging-test-secret`. Some behaviors can be overridden by query string parameters. More than one query string parameter " +
            "can be used. For example `${keyVault:test-secret?ttl=30000&version=1}` would return the secret named `test-secret` version `1` with a TTL of 30 seconds. " +
            "After the TTL has expired the ConfigProvider will request an updated credential. If you're using this with Kafka Connect, your tasks will be reconfigured if one of the values " +
            "have changed." +
            "\n\n" +
            "+-----------+------------------------------------------------+--------------------------------------------------------------------+------------------------------------------+\n" +
            "| Parameter | Description                                    | Default                                                            | Example                                  |\n" +
            "+===========+================================================+====================================================================+==========================================+\n" +
            "| ttl       | Used to override the TTL for the secret.       | Value specified by `config.providers.keyVault.param.secret.ttl.ms` | `${keyVault:test-secret?ttl=60000}`      |\n" +
            "+-----------+------------------------------------------------+--------------------------------------------------------------------+------------------------------------------+\n" +
            "| version   | Used to override the version of the secret.    | latest                                                             | `${keyVault:test-secret?version=1}`      |\n" +
            "+-----------+------------------------------------------------+--------------------------------------------------------------------+------------------------------------------+\n")
    }
)
public class KeyVaultConfigProvider implements ConfigProvider {
  private static final Logger log = LoggerFactory.getLogger(KeyVaultConfigProvider.class);
  KeyVaultConfigProviderConfig config;
  KeyVaultFactory keyVaultFactory = new KeyVaultFactoryImpl();

  SecretClientWrapper secretClient;
  ObjectMapper mapper = new ObjectMapper();

  @Override
  public ConfigData get(String path) {
    return get(path, Collections.emptySet());
  }

  @Override
  public ConfigData get(String p, Set<String> keys) {
    log.info("get() - path = '{}' keys = '{}'", p, keys);
    SecretPath secretPath = SecretPath.parse(this.config, p);
    Path path = secretPath.path();

    try {
      log.debug("Requesting {} from Secrets Manager", path);
      KeyVaultSecret response = secretClient.getSecret(path.toString(), secretPath.version());

      ObjectNode node = mapper.readValue(response.getValue(), ObjectNode.class);

      Set<String> propertiesToRead = (null == keys || keys.isEmpty()) ? ImmutableSet.copyOf(node.fieldNames()) : keys;
      Map<String, String> results = new LinkedHashMap<>(propertiesToRead.size());
      for (String propertyName : propertiesToRead) {
        JsonNode propertyNode = node.get(propertyName);
        if (null != propertyNode && !propertyNode.isNull()) {
          results.put(propertyName, propertyNode.textValue());
        }
      }
      return new ConfigData(results, secretPath.ttl());
    } catch (Exception ex) {
      throw createException(ex, "Exception thrown while reading secret '%s'", path);
    }
  }

  ConfigException createException(Throwable cause, String message, Object... args) {
    String exceptionMessage = String.format(message, args);
    ConfigException configException = new ConfigException(exceptionMessage);
    configException.initCause(cause);
    return configException;
  }

  @Override
  public void close() throws IOException {

  }

  @Override
  public void configure(Map<String, ?> settings) {
    this.config = new KeyVaultConfigProviderConfig(settings);
    this.secretClient = this.keyVaultFactory.create(this.config);
  }

  public static ConfigDef config() {
    return KeyVaultConfigProviderConfig.config();
  }
}
