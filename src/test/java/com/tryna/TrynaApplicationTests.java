package com.tryna;

import com.tryna.domain.event.service.HolidaySyncService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class TrynaApplicationTests {

	@MockitoBean
	private HolidaySyncService holidaySyncService;

	@Test
	void contextLoads() {
	}

}
