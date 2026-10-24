package com.example.lab08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeaIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	@WithMockUser
	void createTea_ShouldInsertRowIntoTable() throws Exception {
		mockMvc.perform(post("/api/v1/teas").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "Да Хун Пао", "teaType": "OOLONG" }
						"""))
				.andExpect(status().isCreated());

		String teaType = jdbc.sql("select tea_type from tea where name = ?")
				.param("Да Хун Пао")
				.query(String.class)
				.single();

		assertThat(teaType).isEqualTo("OOLONG");
	}

	@Test
	@WithMockUser
	void createTea_WithInvalidData_ShouldReturn400AndKeepRowCount() throws Exception {
		long before = jdbc.sql("select count(*) from tea").query(Long.class).single();

		mockMvc.perform(post("/api/v1/teas").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "", "teaType": "" }
						"""))
				.andExpect(status().isBadRequest());

		long after = jdbc.sql("select count(*) from tea").query(Long.class).single();

		assertThat(after).isEqualTo(before);
	}

}
