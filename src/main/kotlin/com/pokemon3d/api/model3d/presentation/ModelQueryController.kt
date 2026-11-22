package com.pokemon3d.api.model3d.presentation

import com.pokemon3d.api.global.dto.ApiResponse
import com.pokemon3d.api.model3d.application.ModelAnimations
import com.pokemon3d.api.model3d.application.ModelCatalogEntry
import com.pokemon3d.api.model3d.application.ModelFilterOptions
import com.pokemon3d.api.model3d.application.ModelResource
import com.pokemon3d.api.model3d.application.ModelResourceService
import com.pokemon3d.api.model3d.application.ModelSearchCriteria
import com.pokemon3d.api.model3d.application.PageResult
import com.pokemon3d.api.model3d.application.PokemonCatalogEntry
import com.pokemon3d.api.model3d.application.PokemonDetail
import com.pokemon3d.api.model3d.application.PokemonQueryService
import com.pokemon3d.api.model3d.application.SortDirection
import com.pokemon3d.api.model3d.domain.ModelCamera
import com.pokemon3d.api.model3d.domain.ModelForm
import com.pokemon3d.api.model3d.domain.ModelMotion
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RequestMapping("/api/v1")
@Tag(name = "포켓몬·모델 조회", description = "포켓몬, 3D 모델, 애니메이션, 검색 필터를 조회하는 API")
interface ModelQueryApi {
    @GetMapping("/pokemon")
    @Operation(summary = "포켓몬 검색", description = "도감 번호 기준으로 포켓몬을 묶어 검색하고 필터와 페이지네이션을 적용합니다.")
    fun searchPokemon(
        @Valid @ParameterObject @ModelAttribute request: ModelSearchRequest,
    ): ApiResponse<PageResponse<PokemonSummaryResponse>>

    @GetMapping("/pokemon/{dexNumber}")
    @Operation(summary = "포켓몬 상세 조회", description = "도감 번호에 해당하는 포켓몬과 보유한 모든 모델 폼을 조회합니다.")
    fun getPokemon(
        @PathVariable @Min(1) @Parameter(description = "전국도감 번호", example = "25") dexNumber: Int,
    ): ApiResponse<PokemonDetailResponse>

    @GetMapping("/models")
    @Operation(summary = "모델 검색", description = "각 폼과 이로치 모델을 개별 항목으로 검색하고 필터·정렬·페이지네이션을 적용합니다.")
    fun searchModels(
        @Valid @ParameterObject @ModelAttribute request: ModelSearchRequest,
    ): ApiResponse<PageResponse<ModelSummaryResponse>>

    @GetMapping("/models/{modelId}")
    @Operation(summary = "모델 상세 조회", description = "모델 메타데이터, 공개 GLB URL, 리비전, 애니메이션, 기본 카메라 정보를 조회합니다.")
    fun getModel(
        @PathVariable @Parameter(description = "모델의 고유 식별자", example = "pikachu") modelId: String,
    ): ApiResponse<ModelDetailResponse>

    @GetMapping("/models/{modelId}/animations")
    @Operation(summary = "모델 애니메이션 조회", description = "모델의 동작 애니메이션과 보조 효과 트랙을 조회합니다.")
    fun getAnimations(
        @PathVariable @Parameter(description = "모델의 고유 식별자", example = "pikachu") modelId: String,
    ): ApiResponse<ModelAnimationsResponse>

    @GetMapping("/filters")
    @Operation(summary = "검색 필터 옵션 조회", description = "현재 활성 데이터셋에서 사용할 수 있는 폼·성별·세대·이로치 값을 조회합니다.")
    fun getFilters(): ApiResponse<ModelFilterOptionsResponse>
}

@RestController
class ModelQueryController(
    private val queries: PokemonQueryService,
    private val resources: ModelResourceService,
) : ModelQueryApi {
    override fun searchPokemon(request: ModelSearchRequest): ApiResponse<PageResponse<PokemonSummaryResponse>> =
        ApiResponse.success(queries.searchPokemon(request.toCriteria()).toResponse(PokemonSummaryResponse::from))

    override fun getPokemon(dexNumber: Int): ApiResponse<PokemonDetailResponse> =
        ApiResponse.success(PokemonDetailResponse.from(queries.findPokemon(dexNumber)))

    override fun searchModels(request: ModelSearchRequest): ApiResponse<PageResponse<ModelSummaryResponse>> =
        ApiResponse.success(queries.searchModels(request.toCriteria()).toResponse(ModelSummaryResponse::from))

    override fun getModel(modelId: String): ApiResponse<ModelDetailResponse> =
        ApiResponse.success(ModelDetailResponse.from(resources.findById(modelId)))

    override fun getAnimations(modelId: String): ApiResponse<ModelAnimationsResponse> =
        ApiResponse.success(ModelAnimationsResponse.from(queries.findAnimations(modelId)))

    override fun getFilters(): ApiResponse<ModelFilterOptionsResponse> =
        ApiResponse.success(ModelFilterOptionsResponse.from(queries.filters()))
}

data class ModelSearchRequest(
    @field:Min(1)
    @field:Schema(description = "전국도감 번호", example = "25")
    val pokedexNumber: Int? = null,
    @field:Size(max = 100)
    @field:Schema(description = "한글명 또는 영문명의 대소문자 구분 없는 부분 검색어")
    val name: String? = null,
    @field:Size(max = 80)
    @field:Schema(description = "폼 코드·종류의 정확한 값 또는 폼 이름의 부분 검색어")
    val form: String? = null,
    @field:Schema(description = "이로치 여부")
    val shiny: Boolean? = null,
    @field:Pattern(regexp = "(?i)^(shared|genderless|male|female)$")
    @field:Schema(description = "모델 성별", allowableValues = ["shared", "genderless", "male", "female"])
    val gender: String? = null,
    @field:Min(1) @field:Schema(description = "포켓몬 세대", example = "1")
    val generation: Int? = null,
    @field:Min(0) @field:Schema(description = "0부터 시작하는 페이지 번호")
    val page: Int = 0,
    @field:Min(1) @field:Max(100) @field:Schema(description = "페이지당 항목 수 (최대 100)")
    val size: Int = 20,
    @field:Pattern(regexp = "^(pokedexNumber|modelId|nameKo|generation)$")
    @field:Schema(description = "정렬 기준", allowableValues = ["pokedexNumber", "modelId", "nameKo", "generation"])
    val sort: String = "pokedexNumber",
    @field:Pattern(regexp = "(?i)^(asc|desc)$")
    @field:Schema(description = "정렬 방향", allowableValues = ["asc", "desc"])
    val direction: String = "asc",
) {
    fun toCriteria() = ModelSearchCriteria(
        pokedexNumber = pokedexNumber,
        name = name,
        form = form,
        shiny = shiny,
        gender = gender,
        generation = generation,
        page = page,
        size = size,
        sort = sort,
        direction = SortDirection.valueOf(direction.uppercase()),
    )
}

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T, R> from(result: PageResult<T>, transform: (T) -> R) = PageResponse(
            result.items.map(transform),
            result.page,
            result.size,
            result.totalElements,
            result.totalPages,
        )
    }
}

data class PokemonSummaryResponse(
    val pokedexNumber: Int,
    val nameKo: String,
    val nameEn: String,
    val generation: Int,
    val modelCount: Int,
    val modelIds: List<String>,
    val forms: List<ModelForm>,
    val hasShiny: Boolean,
) {
    companion object {
        fun from(pokemon: PokemonCatalogEntry) = PokemonSummaryResponse(
            pokemon.pokedexNumber,
            pokemon.nameKo,
            pokemon.nameEn,
            pokemon.generation,
            pokemon.modelCount,
            pokemon.modelIds,
            pokemon.forms,
            pokemon.hasShiny,
        )
    }
}

data class PokemonDetailResponse(
    val pokemon: PokemonSummaryResponse,
    val models: List<ModelSummaryResponse>,
) {
    companion object {
        fun from(detail: PokemonDetail) = PokemonDetailResponse(
            PokemonSummaryResponse.from(detail.pokemon),
            detail.models.map(ModelSummaryResponse::from),
        )
    }
}

data class ModelSummaryResponse(
    val modelId: String,
    val pokedexNumber: Int,
    val nameKo: String,
    val nameEn: String,
    val form: ModelForm,
    val shiny: Boolean,
    val gender: String,
    val generation: Int,
    val animationCount: Int,
) {
    companion object {
        fun from(model: ModelCatalogEntry) = ModelSummaryResponse(
            model.modelId,
            model.pokedexNumber,
            model.nameKo,
            model.nameEn,
            model.form,
            model.shiny,
            model.gender,
            model.generation,
            model.animationCount,
        )
    }
}

data class ModelDetailResponse(
    val datasetVersion: String,
    val revision: Long,
    val modelId: String,
    val pokedexNumber: Int,
    val nameKo: String,
    val nameEn: String,
    val form: ModelForm,
    val shiny: Boolean,
    val gender: String,
    val generation: Int,
    val fileUrl: URI,
    val fileSizeBytes: Long,
    val sha256: String,
    val requiredExtensions: List<String>,
    val animations: List<ModelMotion>,
    val camera: ModelCamera,
) {
    companion object {
        fun from(resource: ModelResource): ModelDetailResponse {
            val model = resource.model
            return ModelDetailResponse(
                resource.datasetVersion,
                resource.revision,
                model.modelId,
                model.pokedexNumber,
                model.nameKo,
                model.nameEn,
                model.form,
                model.shiny,
                model.gender,
                model.generation,
                resource.fileUrl,
                model.asset.sizeBytes,
                model.asset.sha256,
                model.asset.requiredExtensions,
                model.motions,
                model.camera,
            )
        }
    }
}

data class ModelAnimationsResponse(val modelId: String, val animations: List<ModelMotion>) {
    companion object {
        fun from(result: ModelAnimations) =
            ModelAnimationsResponse(result.modelId, result.animations)
    }
}

data class ModelFilterOptionsResponse(
    val forms: List<ModelForm>,
    val genders: List<String>,
    val generations: List<Int>,
    val shinyValues: List<Boolean>,
) {
    companion object {
        fun from(options: ModelFilterOptions) = ModelFilterOptionsResponse(
            options.forms,
            options.genders,
            options.generations,
            options.shinyValues,
        )
    }
}

private fun <T, R> PageResult<T>.toResponse(transform: (T) -> R): PageResponse<R> =
    PageResponse.from(this, transform)
