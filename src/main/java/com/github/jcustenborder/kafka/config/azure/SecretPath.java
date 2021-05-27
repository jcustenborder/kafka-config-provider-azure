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

import org.apache.kafka.common.config.ConfigException;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class SecretPath {
  private final URI uri;
  private final Map<String, String> params;
  private final long ttl;
  private final Path path;
  private final Long version;

  private SecretPath(URI uri, Map<String, String> params, String name, String prefix, Long version, long ttl) {
    this.uri = uri;
    this.params = params;
    this.ttl = ttl;
    this.version = version;

    this.path = Paths.get(
        uri.getPath()
    );
  }

  public long ttl() {
    return this.ttl;
  }


  public Path path() {
    return this.path;
  }

  private static Map<String, String> parseParams(URI uri) {
    Map<String, String> results;

    if (null != uri.getQuery()) {
      results = Stream.of(uri.getQuery().split("&"))
          .map(s -> {
            String[] parts = s.split("=");
            return new AbstractMap.SimpleImmutableEntry<>(parts[0], parts.length == 2 ? parts[1] : null);
          })
          .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey, AbstractMap.SimpleImmutableEntry::getValue));
    } else {
      results = new LinkedHashMap<>();
    }
    return results;
  }

  static Long parseLong(Map<String, String> params, String key, Long defaultValue) {
    Long result;

    if (params.containsKey(key)) {
      String input = params.get(key);
      try {
        result = Long.parseLong(input);
      } catch (NumberFormatException ex) {
        ConfigException configException = new ConfigException(key, input, "Could not parse to long.");
        configException.initCause(ex);
        throw configException;
      }
    } else {
      result = defaultValue;
    }

    return result;
  }

  public static SecretPath parse(KeyVaultConfigProviderConfig config, String input) {
    URI uri = URI.create(config.prefix + input);
    Map<String, String> params = parseParams(uri);
    long ttl = parseLong(params, "ttl", config.minimumSecretTTL);
    Long version = parseLong(params, "version", null);
    return new SecretPath(uri, params, input, config.prefix, version, ttl);
  }

  public String version() {
    return null != this.version ? this.version.toString() : null;
  }
}
