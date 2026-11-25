package com.siquaras.auditlog.configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.siquaras.auditlog.util.StringUtil;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoadSensitiveFields {

	private static Set<String> sensitiveFields = new HashSet<>();
	
	private final ResourceLoader resourceLoader;

	public LoadSensitiveFields(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
	
	@PostConstruct
    public void init() {
        carregarSensiteFieldsTxt();
    }
	
	public void carregarSensiteFieldsTxt() {
		Resource resource = resourceLoader.getResource("classpath:sensitive_fields.txt");

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
			
			reader.lines().map(String::trim)
						  .map(StringUtil::normalize)
						  .filter(s -> !s.isBlank())
						  .forEach(sensitiveFields::add);
		} catch (IOException e) {
			log.error("Arquivo sensitive_fields.txt está faltando em 'classpath', crie o arquivo com os fields que deja mascarar");
			log.error("Error: ", e);
		}
	}

	public static boolean isSensitive(String fieldName) {
		return sensitiveFields.stream().anyMatch(StringUtil.normalize(fieldName)::contains);
	}

	public static Set<String> getSensitiveFields() {
		return Collections.unmodifiableSet(sensitiveFields);
	}
}
