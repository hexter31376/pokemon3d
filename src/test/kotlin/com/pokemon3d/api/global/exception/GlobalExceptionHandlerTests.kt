package com.pokemon3d.api.global.exception

import com.pokemon3d.api.global.dto.ApiResponse
import com.pokemon3d.api.global.dto.ErrorCode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.*

class GlobalExceptionHandlerTests {
    private val mvc = MockMvcBuilders.standaloneSetup(TestController())
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @Test
    fun `success response contains data`() {
        mvc.perform(get("/test").param("id", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.error").isEmpty)
    }

    @Test
    fun `business exception preserves its status and code`() {
        mvc.perform(get("/test/business"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").isEmpty)
            .andExpect(jsonPath("$.error.code").value("COMMON_008"))
    }

    @Test
    fun `unexpected exception hides internal details`() {
        mvc.perform(get("/test/unexpected"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error.code").value("COMMON_010"))
            .andExpect(jsonPath("$.error.message").value("내부 서버 오류가 발생했습니다."))
    }

    @Test
    fun `missing and malformed parameters are bad requests`() {
        for (request in listOf(get("/test"), get("/test").param("id", "abc"))) {
            mvc.perform(request).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("COMMON_001"))
        }
    }

    @Test
    fun `malformed JSON has its own code`() {
        mvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_002"))
    }

    @Test
    fun `validation includes field errors`() {
        mvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("""{"name":""}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))
            .andExpect(jsonPath("$.error.fields[0].field").value("name"))
            .andExpect(jsonPath("$.error.fields[0].message").value("이름은 필수입니다."))
            .andExpect(jsonPath("$.error.fields[0].rejectedValue").doesNotExist())
    }

    @Test
    fun `method not allowed preserves Allow header`() {
        mvc.perform(post("/test/business"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(header().string("Allow", "GET"))
            .andExpect(jsonPath("$.error.code").value("COMMON_006"))
    }

    @Test
    fun `unsupported content type remains 415`() {
        mvc.perform(post("/test").contentType(MediaType.TEXT_PLAIN).content("hello"))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.error.code").value("COMMON_009"))
    }

    @Test
    fun `unknown route remains 404`() {
        mvc.perform(get("/missing"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("COMMON_005"))
    }

    @RestController
    class TestController {
        @GetMapping("/test")
        fun get(@RequestParam("id") id: Int) = ApiResponse.success(mapOf("id" to id))

        @PostMapping("/test")
        fun create(@Valid @RequestBody body: TestRequest) = ApiResponse.success(body)

        @GetMapping("/test/business")
        fun business(): Unit = throw BusinessException(ErrorCode.CONFLICT)

        @GetMapping("/test/unexpected")
        fun unexpected(): Unit = throw IllegalStateException("secret database credentials")
    }

    data class TestRequest(@field:NotBlank(message = "이름은 필수입니다.") val name: String)
}
