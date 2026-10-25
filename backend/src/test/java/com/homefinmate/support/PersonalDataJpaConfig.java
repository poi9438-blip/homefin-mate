package com.homefinmate.support;

import com.homefinmate.migration.V5__ProtectPersonalData;
import com.homefinmate.util.EmailHasher;
import com.homefinmate.util.EncryptionUtil;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

// @DataJpaTest에 암호화·해시 도구와 V5 마이그레이션 추가
@TestConfiguration
@Import({ EncryptionUtil.class, EmailHasher.class, V5__ProtectPersonalData.class })
public class PersonalDataJpaConfig {
}
