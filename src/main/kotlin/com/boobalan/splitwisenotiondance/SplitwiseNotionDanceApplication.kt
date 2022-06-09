package com.boobalan.splitwisenotiondance

import com.boobalan.splitwisenotiondance.common.TransactionData
import com.boobalan.splitwisenotiondance.notion.*
import com.boobalan.splitwisenotiondance.notion.SplitwiseUtils.Companion.retryIO
import com.boobalan.splitwisenotiondance.splitwise.Expense
import com.boobalan.splitwisenotiondance.splitwise.ExpenseInfo
import com.boobalan.splitwisenotiondance.splitwise.User
import com.boobalan.splitwisenotiondance.splitwise.UserInfo
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.util.UriBuilder
import java.math.BigDecimal
import java.net.URI
import java.time.*
import java.util.*


public val log = KotlinLogging.logger {}


@SpringBootApplication
class SplitwiseNotionDanceApplication

fun main(args: Array<String>) {
    runApplication<SplitwiseNotionDanceApplication>(*args)
}

fun getPropertyValueNode(objectMapper: ObjectMapper, propertyName: String?, propertyValue: String?): ObjectNode {
    // for each property
    val propertyValueNode = objectMapper.createObjectNode()
    when (propertyName) {
        "Source" -> propertyValueNode.putPOJO("select", Collections.singletonMap("name", propertyValue))
        "Amount" -> propertyValueNode.put("number", BigDecimal(propertyValue))
        "Date" -> {
            //                String dateString = "2022-05-20";
            val dateString = ZonedDateTime.ofInstant(Instant.parse(propertyValue), ZoneId.systemDefault())
                .toLocalDate().toString()
            propertyValueNode.putPOJO("date", Collections.singletonMap("start", dateString))
        }
        "Description" -> {
            val titleNode = objectMapper.createArrayNode()
            val titleValueNode = objectMapper.createObjectNode()
            titleValueNode.put("type", "text")
            titleValueNode.putPOJO("text", Collections.singletonMap("content", propertyValue))
            titleNode.add(titleValueNode)
            propertyValueNode.set<JsonNode>("title", titleNode)
        }
    }
    return propertyValueNode
}

@Configuration
class WebClientConfiguration {

    @Bean
    fun webClient(
        clientRegistrations: ReactiveClientRegistrationRepository,
        authorizedClients: ServerOAuth2AuthorizedClientRepository
    ): WebClient {
        val oauth = ServerOAuth2AuthorizedClientExchangeFilterFunction(
            clientRegistrations,
            authorizedClients
        )
        oauth.setDefaultOAuth2AuthorizedClient(false)
        return WebClient.builder()
            .filter(oauth)
            .build()
    }
}

@RestController
class HelloController(private val webClient: WebClient) {

    @GetMapping("/world")
    fun world(): String {
        return "hello world"
    }

    @GetMapping("/universe")
    fun universe(): String {
        return "hello universe"
    }

    @GetMapping("/splitwise")
    suspend fun splitwise(
        @RegisteredOAuth2AuthorizedClient("splitwise") authorizedSplitwiseClient: OAuth2AuthorizedClient?,
        userPrincipal: Authentication?
    ): String {
        val responseString = webClient
            .get()
            .uri("https://secure.splitwise.com/api/v3.0/get_current_user")
            .attributes(
                ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(
                    authorizedSplitwiseClient
                )
            )
            .retrieve().awaitBody<String>()
        return responseString.let { s: String -> "welcome to splitwise: $s" }
    }

    @GetMapping("/notion")
    suspend fun notion(
        @RegisteredOAuth2AuthorizedClient("notion") authorizedNotionClient: OAuth2AuthorizedClient?,
        authentication: Authentication?
    ): String {
        val responseString = webClient
            .post()
            .uri("https://api.notion.com/v1/search")
            .header("Notion-Version", "2022-02-22")
            .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(authorizedNotionClient)) //                .bodyValue("{}")
            .retrieve()
            .awaitBody<String>()
        return responseString.let { s: String -> "welcome to notion: $s" }
        //        return Mono.just("welcome to notion");
    }

    @GetMapping("/okta")
    fun okta(@RegisteredOAuth2AuthorizedClient("okta") splitwise: OAuth2AuthorizedClient?): String {
        return "welcome to okta"
    }
}

@RestController
@RequestMapping(path = ["/webclient", "/public/webclient"])
class OAuth2WebClientController(private val webClient: WebClient, private val objectMapper: ObjectMapper) {

    //    @GetMapping("/splitwise")
    //    Mono<String> splitwise() {
    //        // @formatter:off
    //        Mono<String> responseString = this.webClient
    //                .get()
    //                .uri("https://secure.splitwise.com/api/v3.0/get_current_user")
    //                .attributes(clientRegistrationId("splitwise"))
    //                .retrieve()
    //                .bodyToMono(String.class);
    //        // @formatter:on
    //        return responseString.map(s -> "welcome to splitwise: " + s);
    //    }
    @GetMapping("/splitwise/user")
    suspend fun getSplitwiseCurrentUser(): ResponseEntity<*> {

        // @formatter:off
        val responseString = webClient
            .get()
            .uri("https://secure.splitwise.com/api/v3.0/get_current_user")
            .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("splitwise"))
            .retrieve()
            .awaitBody<String>()
        //        URI.create()
        // @formatter:on

        log.info { "Welcome to splitwise$responseString" }

        return ResponseEntity.status(
            HttpStatus.FOUND
        ).header(
            HttpHeaders.LOCATION,
            URI.create("/webclient/notion").toString()
        ).build<Any?>()
    }

    @GetMapping("/splitwise/expense/withretry")  // todo this throws exception
    suspend fun getLimitedExpenseForSplitwise(
        @RequestParam(value = "limit", defaultValue = "20") limit: Int,
        @RequestParam(value = "offset", defaultValue = "0") offset: Int,
        @RequestParam(value = "dated_after", required = true) dated_after: String,
        @RequestParam(value = "dated_before", required = true) dated_before: String
    ): ExpenseInfo {

//        Instant datedAfter = ZonedDateTime.of(LocalDate.of(2022, Month.JANUARY, 1), LocalTime.MIN, ZoneId.of("America/Los_Angeles")).toInstant();
        val datedAfter =
            ZonedDateTime.of(LocalDate.parse(dated_after), LocalTime.MIN, ZoneId.of("America/Los_Angeles")).toInstant()
        //        Instant datedBefore = ZonedDateTime.of(LocalDate.of(2022, Month.MAY, 23), LocalTime.MIN, ZoneId.of("America/Los_Angeles")).toInstant();
        val datedBefore =
            ZonedDateTime.of(LocalDate.parse(dated_before), LocalTime.MIN, ZoneId.of("America/Los_Angeles")).toInstant()

        return retryIO { // todo the client authorization request page from splitwise doesn't surface upto webflux, why? ask a question in stackoverflow
            getExpenseFromSplitwiseAPI(datedAfter, datedBefore, limit, offset)
        }
    }

    @GetMapping("/splitwise/expense/plain")
    suspend fun getLimitedExpenseForSplitwisePlain(
        @RequestParam(value = "limit", defaultValue = "20") limit: Int,
        @RequestParam(value = "offset", defaultValue = "0") offset: Int,
        @RequestParam(value = "dated_after", required = true) dated_after: String,
        @RequestParam(value = "dated_before", required = true) dated_before: String
    ): ExpenseInfo {

//        Instant datedAfter = ZonedDateTime.of(LocalDate.of(2022, Month.JANUARY, 1), LocalTime.MIN, ZoneId.of("America/Los_Angeles")).toInstant();
        val datedAfter =
            ZonedDateTime.of(LocalDate.parse(dated_after), LocalTime.MIN, ZoneId.of("America/Los_Angeles")).toInstant()
        //        Instant datedBefore = ZonedDateTime.of(LocalDate.of(2022, Month.MAY, 23), LocalTime.MIN, ZoneId.of("America/Los_Angeles")).toInstant();
        val datedBefore =
            ZonedDateTime.of(LocalDate.parse(dated_before), LocalTime.MIN, ZoneId.of("America/Los_Angeles")).toInstant()

        return getExpenseFromSplitwiseAPI(datedAfter, datedBefore, limit, offset)

    }

    private suspend fun getExpenseFromSplitwiseAPI(
        datedAfter: Instant,
        datedBefore: Instant,
        limit: Int,
        offset: Int
    ): ExpenseInfo = webClient
        .get() // dated_after, updated_after
        .uri(
            HTTPS_SECURE_SPLITWISE_COM_API_V_3_0
        ) { uriBuilder: UriBuilder ->
            uriBuilder.path("/get_expenses")
                .queryParam("dated_after", datedAfter.toString())
                .queryParam("dated_before", datedBefore.toString())
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .build()
        }
        .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("splitwise"))
        .retrieve() //                .bodyToMono(String.class)
        .awaitBody()

    @GetMapping("/splitwise/expense/allpage")
    fun getAllSplitwiseExpenses(
        @RequestParam(value = "limit", defaultValue = "20") limit: Int,
        @RequestParam(value = "offset", defaultValue = "0") offset: Int,
        @RequestParam(value = "dated_after", required = true) dated_after: String,
        @RequestParam(value = "dated_before", required = true) dated_before: String
    ): Flow<ExpenseInfo> {
        // ideas from https://stackoverflow.com/a/53370449/6270888, https://stackoverflow.com/a/53789227/6270888 and https://stackoverflow.com/a/50387669/6270888

        return flow {

        var currentPageIndex = 0;
            do {
                val expenseInfo = getLimitedExpenseForSplitwise(limit, offset + (currentPageIndex++ * limit) , dated_after, dated_before)

                emit(expenseInfo)
//                delay(500)
                val endOfList = expenseInfo.expenses?.size!! < limit
            } while (!endOfList)

        }

    }


    @GetMapping("/notion/search")
    suspend fun searchForDatabaseInNotion(): NotionSearchResult {

        // todo set global throttle limit for api calls
        val notionSearchResult = webClient
            .post()
            .uri("$HTTPS_API_NOTION_COM_V_1/search")
            .header("Notion-Version", "2022-02-22")
            .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("notion"))
            .bodyValue(Query("Test Log", Filter("object", "database")))
            .retrieve()
            .awaitBody<NotionSearchResult>();

        return notionSearchResult.also { log.debug { it.toString() } }


    }

    @GetMapping("/notion/database")
    suspend fun getNotionDatabasePagesDetail(): NotionDatabasePageList {
        // @formatter:off
        val notionSearchResult: NotionSearchResult = searchForDatabaseInNotion()

        assert(notionSearchResult.results?.size == 1)

        return webClient
            .post()
            .uri(
                HTTPS_API_NOTION_COM_V_1
            ) { uriBuilder: UriBuilder ->
                uriBuilder.path("/databases/{database_id}/query")
                    .build(
                        notionSearchResult.results?.get(0)?.id
                            ?: throw IllegalArgumentException("notion database cannot be null ")
                    )
            }
            .header("Notion-Version", "2022-02-22")
            .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("notion"))
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve()
            .awaitBody()
    }

    @RequestMapping(
        value = ["/notion/page"],
        method = [RequestMethod.POST],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun getNotionPage(): String {
        // @formatter:off
        val notionSearchResult: NotionDatabasePageList = getNotionDatabasePagesDetail()

        return webClient
            .get()
            .uri(
                HTTPS_API_NOTION_COM_V_1
            ) { uriBuilder: UriBuilder ->
                uriBuilder.path("/pages/{page_id}/").build(
                    notionSearchResult.results?.get(0)?.id
                        ?: throw IllegalArgumentException("notion search result cannot be null")
                )
            }
            .header("Notion-Version", "2022-02-22")
            .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("notion"))
            .retrieve()
            .awaitBody()
    }


    // todo this should not be a get method as this call changes state

    @RequestMapping(
        value = ["/notion/page"],
        method = [RequestMethod.GET],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun notionPageCreate(
        @RequestParam(value = "dated_after", required = true) dated_after: String,
        @RequestParam(value = "dated_before", required = true) dated_before: String
    ): Map<String, JsonNode> {

        // getting notion database_id to send the data to; from Notion API's
        val notionDatabaseId = databaseIdInNotion()

        // getting data from Splitwise
        val splitwiseExpenseFlow = getAllSplitwiseExpenses(20, 0, dated_after, dated_before)

        val processedExpenseDataFromSplitwise = splitwiseExpenseFlow
            .mapNotNull {
                it.expenses
                    ?.filterNot { expense ->
                        isExpenseAutocreatedBySplitwise(expense)
                    }
                    ?.mapNotNull { expense ->
                        val users: List<User> = expense.users ?: emptyList()

                        // this adds 0  value transaction too
                        val moneyOwed: BigDecimal? = users.asSequence()
                            .filter { user ->
                                isItAmrita(user)
                            }

                            .map { user -> BigDecimal(user.net_balance) }
                            .filter { netBalance -> doesTheUserOweMoney(netBalance) }

                            .map(BigDecimal::negate)
                            .reduceOrNull(BigDecimal::add)

                        moneyOwed?.let {
                            TransactionData().copy(source = "Splitwise")
                                .copy(description = expense.description).copy(date = expense.date)
                                .copy(amount = moneyOwed.toString())
                        }

                    }
                    ?.asFlow()
            }
            .flattenConcat()
            .buffer()  // make the coroutines above this operator independent of the coroutine below this operator

        // @formatter:off
        // final result to return in thi API's response
        val resultMap = mutableMapOf<String, JsonNode>()

        // transfer processed expense data from splitwise to notion; as notion pages
        processedExpenseDataFromSplitwise
            // todo to ask question in stackoverflow comparing Flux.delayElements and onEach { delay(500) }
            // to satisfy throttle limit of webclient apis
//            .onEach { delay(500) }
            .map {
                val propertiesNode = transactionDataToPropertiesNode(it)
                val notionPage: NotionPage = NotionPage()
                    .copy(parent = ParentDatabase().copy(database_id = notionDatabaseId))
                    .copy(properties = propertiesNode)
                createPageInNotion(notionPage)
            }
            .collect {

                try {
                    val value = objectMapper.readTree(it)
                    val key = value["id"].asText()
                    resultMap[key] = value
                } catch (e: JsonProcessingException) {
                    throw RuntimeException(e)
                }

            }

        // @formatter:on
        return resultMap
    }

    private suspend fun databaseIdInNotion(): String {
        val notionSearchResult = searchForDatabaseInNotion()

        assert(
            (notionSearchResult.results?.size
                ?: throw IllegalArgumentException("notion search result cannot be null")) == 1
        )
        return notionSearchResult.results[0].id
    }

    private fun isExpenseAutocreatedBySplitwise(expense: Expense) =
        expense.description.equals("Payment", true) or expense.description.equals(
            "Settle all balances",
            true
        )

    private suspend fun createPageInNotion(notionPage: NotionPage): String {
        return retryIO {
            webClient
                .post()
                .uri(
                    HTTPS_API_NOTION_COM_V_1
                ) { uriBuilder: UriBuilder ->
                    uriBuilder.path("/pages").build()
                }
                .body(
                    BodyInserters.fromValue<Any>(
                        notionPage
                    )
                )
                .header("Notion-Version", "2022-02-22")
                .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("notion"))
                .retrieve()
                .awaitBody<String>()
        }
    }

    private fun doesTheUserOweMoney(netBalance: BigDecimal) = netBalance < BigDecimal.ZERO

    private fun isItAmrita(user: User): Boolean {
        val userInfo: UserInfo = user.user as UserInfo
        return "Sundari".equals(
            userInfo.last_name,
            ignoreCase = true
        ) && "Amrita".equals(userInfo.first_name, ignoreCase = true)
    }

    @RequestMapping(
        value = ["/notion/page/single"],
        method = [RequestMethod.GET],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun notionPageCreateSingle(): Map<String, JsonNode> {
        val databaseIdInNotion = databaseIdInNotion()
        val transactionData: TransactionData =
            TransactionData().copy(amount = "202").copy(date = "2022-05-20").copy(description = "Test Entry")
                .copy(source = "Splitwise")


        // final result to return in response
        val resultMap = mutableMapOf<String, JsonNode>()

        flowOf(transactionData)
            .onEach {
                delay(500)
            }
            .map {
                val propertiesNode = transactionDataToPropertiesNode(it)
                val notionPage: NotionPage = NotionPage()
                    .copy(parent = ParentDatabase().copy(database_id = databaseIdInNotion))
                    .copy(properties = propertiesNode)
                createPageInNotion(notionPage)
            }
            .collect {
                try {
                    val value = objectMapper.readTree(it)
                    val key = value["id"].asText()
                    resultMap[key] = value
                } catch (e: JsonProcessingException) {
                    throw RuntimeException(e)
                }
            }
        return resultMap
    }

    private fun transactionDataToPropertiesNode(transactionData: TransactionData): ObjectNode {
        val propertiesNode = objectMapper.createObjectNode()
        val sourceNode: ObjectNode = getPropertyValueNode(
            objectMapper,
            "Source",
            transactionData.source
        )
        val amountNode: ObjectNode = getPropertyValueNode(
            objectMapper,
            "Amount",
            transactionData.amount
        )
        val dateNode: ObjectNode = getPropertyValueNode(
            objectMapper,
            "Date",
            transactionData.date
        )
        val descriptionNode: ObjectNode =
            getPropertyValueNode(
                objectMapper,
                "Description",
                transactionData.description
            )
        propertiesNode.set<JsonNode>("Source", sourceNode)
        propertiesNode.set<JsonNode>("Amount", amountNode)
        propertiesNode.set<JsonNode>("Date", dateNode)
        propertiesNode.set<JsonNode>("Description", descriptionNode)
        return propertiesNode
    }

    companion object {
        const val HTTPS_API_NOTION_COM_V_1 = "https://api.notion.com/v1"
        const val HTTPS_SECURE_SPLITWISE_COM_API_V_3_0 = "https://secure.splitwise.com/api/v3.0"
    }
}

@EnableWebFluxSecurity
class OAuth2ClientSecurityConfig {

    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {


        http.authorizeExchange()
            .anyExchange()
            .authenticated()
            .and()
            .oauth2Client()
            .and()
            .formLogin()
        return http.build()
    }
}
