package controllers

import akka.actor._
import akka.stream.Materializer
import aview.TUI
import com.google.inject.Guice
import com.mohiva.play.silhouette
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.impl.User
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import controller.controllerComponent.{ ControllerInterface, DungeonChanged, Earthquake }
import javax.inject._
import main.TrailRunnerModule
import model.levelComponent.levelBaseImpl.{ Level1, Level2, Level3, Level4, Level5, Level6, Level7 }
import play.api.Environment
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import play.twirl.api.HtmlFormat
import java.io.File

import scala.concurrent.ExecutionContext
import scala.swing.Reactor

@Singleton
class TrailRunnerController @Inject() (scc: SilhouetteControllerComponents)(implicit system: ActorSystem, mat: Materializer, ex: ExecutionContext) extends SilhouetteController(scc) {
  val injector = Guice.createInjector(new TrailRunnerModule)

  val gameController = injector.getInstance(classOf[ControllerInterface])
  val tui = new TUI(gameController)
  gameController.publish(new DungeonChanged)

  def about() = silhouette.SecuredAction { implicit request: Request[AnyContent] =>
    Ok.sendFile(new File("./public/TrailRunnerFrontend/index.html"), inline = true)
  }

  def sandbox() = Action {
    Ok("200")
  }

  def save() = Action {
    gameController.save
    Ok("200")
  }

  def load() = Action {
    gameController.load(gameController.getLevelAsJson, true)
    Ok("200")
  }

  def loadCustomGame = Action { request =>
    val json = request.body.asJson.get
    gameController.load(json, false)
    Ok
  }

  def changeToLevelSelection() = Action {
    gameController.changeToSelection()
    Ok("200")
  }

  def changeToGame(levelId: Long) = Action {
    if (levelId == 1) {
      gameController.initializeGame(new Level1, false)
    } else if (levelId == 2) {
      gameController.initializeGame(new Level2, false)
    } else if (levelId == 3) {
      gameController.initializeGame(new Level3, false)
    } else if (levelId == 4) {
      gameController.initializeGame(new Level4, false)
    } else if (levelId == 5) {
      gameController.initializeGame(new Level5, false)
    } else if (levelId == 6) {
      gameController.initializeGame(new Level6, false)
    } else if (levelId == 7) {
      gameController.initializeGame(new Level7, false)
    }
    gameController.changeToGame()
    Ok("200")
  }

  def moveUp() = Action {
    val madeMove: Boolean = gameController.playerMoveUp()
    Ok(Json.obj(
      "madeMove" -> madeMove
    ))
  }

  def moveDown() = Action {
    val madeMove: Boolean = gameController.playerMoveDown()
    Ok(Json.obj(
      "madeMove" -> madeMove
    ))
  }

  def moveLeft() = Action {
    val madeMove: Boolean = gameController.playerMoveLeft()
    Ok(Json.obj(
      "madeMove" -> madeMove
    ))
  }

  def moveRight() = Action {
    val madeMove: Boolean = gameController.playerMoveRight()
    Ok(Json.obj(
      "madeMove" -> madeMove
    ))
  }

  def undo() = Action {
    gameController.undo
    Ok("200")
  }

  def redo() = Action {
    gameController.redo
    Ok("200")
  }

  def menu() = silhouette.SecuredAction { implicit request: Request[AnyContent] =>
    gameController.changeToMain()
    Ok.sendFile(new File("./public/TrailRunnerFrontend/index.html"), inline = true)
  }

  def switchHardcoreMode() = Action {
    gameController.setHardcoreMode(!gameController.getHardcoreMode())
    Ok(Json.obj("hardcoreMode" -> gameController.getHardcoreMode()))
  }

  def win() = Action {
    Ok("200")
  }

  def lose() = Action {
    Ok("200")
  }

  def getMoveJson(yModifier: Int, xModifier: Int): JsObject = {
    var isSliding = gameController.level.dungeon(gameController.player.yPos)(gameController.player.xPos).fieldType == "Ice" &&
      gameController.level.dungeon(gameController.player.yPos)(gameController.player.xPos).value >= 0 &&
      gameController.level.dungeon(gameController.player.yPos - yModifier)(gameController.player.xPos - xModifier).fieldType != "Wall"

    Json.obj(
      "lose" -> gameController.levelLose(),
      "levelFieldSum" -> gameController.level.sum(),
      "doorX" -> gameController.level.doorX,
      "doorY" -> gameController.level.doorY,
      "doorField" -> Json.obj(
        "fieldvalue" -> gameController.level.dungeon(gameController.level.doorY)(gameController.level.doorX).value,
        "fieldtype" -> "Door"
      ),
      "playerY" -> (gameController.player.yPos + yModifier),
      "playerX" -> (gameController.player.xPos + xModifier),
      "playerField" -> Json.obj(
        "fieldvalue" -> gameController.level.dungeon(gameController.player.yPos + yModifier)(gameController.player.xPos + xModifier).value,
        "fieldtype" -> gameController.level.dungeon(gameController.player.yPos + yModifier)(gameController.player.xPos + xModifier).fieldType,
        "fog" -> gameController.level.dungeon(gameController.player.yPos + yModifier)(gameController.player.xPos + xModifier).fog
      ),
      "newPlayerY" -> gameController.player.yPos,
      "newPlayerX" -> gameController.player.xPos,
      "newPlayerField" -> Json.obj(
        "fieldvalue" -> (gameController.level.dungeon(gameController.player.yPos)(gameController.player.xPos).value),
        "fieldtype" -> gameController.level.dungeon(gameController.player.yPos)(gameController.player.xPos).fieldType,
        "fog" -> gameController.level.dungeon(gameController.player.yPos)(gameController.player.xPos).fog
      ),
      "isSliding" -> isSliding)
  }

  def getChangedFields(move: String) = silhouette.SecuredAction { implicit request =>
    var returnObject: JsObject = null;
    if (move == "up") {
      returnObject = getMoveJson(1, 0)
    } else if (move == "down") {
      returnObject = getMoveJson(-1, 0)
    } else if (move == "left") {
      returnObject = getMoveJson(0, 1)
    } else {
      returnObject = getMoveJson(0, -1)
    }
    Ok(returnObject)
  }

  def getLevelMap() = silhouette.SecuredAction { implicit request =>
    Ok(gameController.getLevelAsJson)
  }

  def isGameOver(): String = {
    if (gameController.levelWin()) {
      "win"
    } else if (gameController.levelLose()) {
      "lose"
    } else {
      "continue"
    }
  }

  def socket = WebSocket.accept[JsValue, JsValue] { _ =>
    ActorFlow.actorRef {
      actorRef => Props(new TrailRunnerWebSocketActor(actorRef))
    }
  }

  class TrailRunnerWebSocketActor(out: ActorRef) extends Actor with Reactor {
    listenTo(gameController)

    reactions += {
      case event: Earthquake =>
        gameController.earthquake()
        out ! buildJsObject("earthquake", gameController.getLevelAsJson)
      case event: DungeonChanged =>
        out ! buildJsObject("dungeon-changed", gameController.getLevelAsJson)
    }

    def buildJsObject(event: String, value: JsValue): String = {
      Json.obj("event" -> event, "value" -> value).toString()
    }

    def receive = {
      case "ping" => out ! Json.obj("alive" -> "pong")
    }
  }
}
