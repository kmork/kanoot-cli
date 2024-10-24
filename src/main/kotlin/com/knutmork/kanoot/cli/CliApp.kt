package com.knutmork.kanoot.cli

import kotlinx.cli.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    val parser = ArgParser("kanoot-cli")

    val command = parser.subcommands(
        AddGameCommand(),
        AddPlayerCommand(),
        SetNicknameCommand(),
        ShowLeaderboardCommand(),
        ListGamesCommand(),
        RemoveGameCommand(),
        ResetGameCommand(),
        QuestionCommand()
    )

    parser.parse(args)
}

abstract class BaseCommand(name: String, description: String) : Subcommand(name, description) {
    protected val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
    }
}

class AddGameCommand : BaseCommand("add-game", "Add a new game") {
    private val title by argument(ArgType.String, description = "Title of the game")

    override fun execute() = runBlocking {
        val response: HttpResponse = client.post("http://localhost:8080/games/initGame") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("title" to title))
        }
        println("Game added: ${response.bodyAsText()}")
    }
}

class AddPlayerCommand : BaseCommand("add-player", "Add a player to a game") {
    private val pin by argument(ArgType.String, description = "Game PIN")

    override fun execute() = runBlocking {
        val response: HttpResponse = client.post("http://localhost:8080/play/$pin/joinGame") {
            contentType(ContentType.Application.Json)
        }
        println("Player added: ${response.bodyAsText()}")
    }
}

class SetNicknameCommand : BaseCommand("set-nickname", "Set the nickname of a player") {
    private val pin by argument(ArgType.String, description = "Game PIN")
    private val playerId by argument(ArgType.String, description = "Player ID")
    private val nickname by argument(ArgType.String, description = "Player nickname")

    override fun execute() = runBlocking {
        val response: HttpResponse = client.post("http://localhost:8080/play/$pin/player/$playerId/nickname") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("nickname" to nickname))
        }
        println("Nickname set: ${response.bodyAsText()}")
    }
}

class ShowLeaderboardCommand : BaseCommand("show-leaderboard", "Show the leaderboard of a game") {
    private val uuid by argument(ArgType.String, description = "Game UUID")

    override fun execute() = runBlocking {
        val response: HttpResponse = client.get("http://localhost:8080/games/$uuid/leaderboard")
        println("Leaderboard: ${response.bodyAsText()}")
    }
}

class ListGamesCommand : BaseCommand("list-games", "Show all games - admin only") {
    override fun execute() = runBlocking {
        val response: HttpResponse = client.get("http://localhost:8080/admin/games")
        println(response.bodyAsText())
    }
}

class RemoveGameCommand : BaseCommand("remove-game", "Deletes a game") {
    private val uuid by argument(ArgType.String, description = "Game UUID")

    override fun execute() = runBlocking {
        val response: HttpResponse = client.post("http://localhost:8080/admin/games/$uuid/remove")
        println(response.bodyAsText())
    }
}

class ResetGameCommand : BaseCommand("reset-game", "Reset the state of a game. Players and questions are still present") {
    private val uuid by argument(ArgType.String, description = "Game UUID")

    override fun execute() = runBlocking {
        val response: HttpResponse = client.post("http://localhost:8080/admin/games/$uuid/reset")
        println(response.bodyAsText())
    }
}

@Serializable
data class Alternative(
    val id: Int,
    val answer: String,
    val correct: Boolean
)

@Serializable
data class Question(
    val questionNumber: Int,
    val timeInSeconds: Int,
    val alternatives: List<Alternative>
)

// Example:
//  kanoot question 1373 1 30 '[{"id":1,"answer":"Answer 1","correct":true},{"id":2,"answer":"Answer 2","correct":false}]'
class QuestionCommand : BaseCommand("question", "Add a question to a game") {
    private val gameId by argument(ArgType.String, description = "Game ID")
    private val questionNumber by argument(ArgType.Int, description = "Question number")
    private val timeInSeconds by argument(ArgType.Int, description = "Time in seconds")
    private val alternatives by argument(ArgType.String, description = "Alternatives in JSON format")

    override fun execute() = runBlocking {
        try {
            val alternativesList = Json.decodeFromString<List<Alternative>>(alternatives)
            val question = Question(
                questionNumber = questionNumber,
                timeInSeconds = timeInSeconds,
                alternatives = alternativesList
            )

            val response: HttpResponse = client.post("http://localhost:8080/games/$gameId/question") {
                contentType(ContentType.Application.Json)
                setBody(question)
            }
            println("Question added: ${response.bodyAsText()}")
        } catch (e: Exception) {
            println("Failed to add question: ${e.message}")
        }
    }
}