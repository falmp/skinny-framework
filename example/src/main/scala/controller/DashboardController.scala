package controller

import org.joda.time._
import model._

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import javax.servlet.http.HttpServletRequest

case class DashboardOps(controller: DashboardController) {
  def setCurrentUser(implicit req: HttpServletRequest) = {
    val userId = controller.currentUserId.getOrElse(controller.halt(401))
    controller.set("currentUser" -> controller.adminUserService.getCurrentUser(userId))
  }
}

class DashboardController extends ApplicationController {

  override implicit def request: HttpServletRequest = {
    val stackTraces = Thread.currentThread().getStackTrace.drop(2).take(20)
    if (stackTraces.exists(_.toString.contains("Future.scala"))) {
      println()
      stackTraces.foreach(s => println("  " + s))
      println()
      throw new RuntimeException("#request within Future detected")
    }
    super.request
  }

  val adminUserService = new AdminUserService
  val accessService = new AccessLogService
  val alertService = new AlertService
  val ops = DashboardOps(this)

  def index = warnElapsedTimeWithRequest(500) {
    if (currentUserId(request).isEmpty) session += "userId" -> 1

    val scope: scala.collection.concurrent.Map[String, Any] = requestScope()

    awaitFutures(5.seconds)(

      // simply define operation inside of this controller
      futureWithRequest { implicit req =>
        // [error] example/src/main/scala/controller/DashboardController.scala:43: ambiguous implicit values:
        // [error]  both value req of type javax.servlet.http.HttpServletRequest
        // [error]  and method request in class DashboardController of type => javax.servlet.http.HttpServletRequest
        // [error]  match expected type javax.servlet.http.HttpServletRequest
        // [error]         set("hourlyStats", accessService.getHourlyStatsForGraph(new LocalDate))
        // [error]            ^
        // [error] one error found

        //set("hourlyStats", accessService.getHourlyStatsForGraph(new LocalDate))
        set("hourlyStats", accessService.getHourlyStatsForGraph(new LocalDate))(req)
      },

      // separate operation to outside of this controller
      futureWithRequest(req => ops.setCurrentUser(req)),

      // just use Future directly
      Future {
        // When using Future directly, you must be aware of Scalatra's DynamicScope's thread local request.
        // In this case, you cannot use request here. requestScope, session and so on should be captured outside of this Future value.
        scope.update("alerts", alertService.findAll())
      }
    )
    render("/dashboard/index")
  }

  private[controller] def currentUserId(implicit req: HttpServletRequest): Option[Long] = session(req).getAs[Long]("userId")

  class AdminUserService extends skinny.util.TimeLogging {

    private[this] val users = Seq(AdminUser(1, "Alice", "alice@example.com"))

    def getCurrentUser(id: Long): Option[AdminUser] = warnElapsedTime(100) {
      Thread.sleep(50L)
      users.find(_.id == id)
    }
  }

  class AccessLogService extends skinny.util.TimeLogging {

    def getHourlyStatsForGraph(date: LocalDate): Seq[HourlyStat] = warnElapsedTime(200) {
      Thread.sleep(300L)
      Seq(
        HourlyStat("2014072000", 224.5, 200000, 21),
        HourlyStat("2014072001", 254.0, 170000, 4),
        HourlyStat("2014072002", 180.2, 92000, 0),
        HourlyStat("2014072003", 192.8, 64000, 0),
        HourlyStat("2014072004", 160.1, 41000, 0),
        HourlyStat("2014072005", 201.5, 58000, 10),
        HourlyStat("2014072006", 724.2, 78000, 7453),
        HourlyStat("2014072007", 157.7, 91000, 15),
        HourlyStat("2014072008", 189.3, 155000, 0)
      )
    }
  }

  class AlertService extends skinny.util.TimeLogging {

    def findAll(): Seq[String] = warnElapsedTime(200) {
      Thread.sleep(150L)
      Seq(
        "New Record!!!",
        "Please register master data."
      )
    }
  }

}

