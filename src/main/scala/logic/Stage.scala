package logic

import logic.Direction
import org.yaml.snakeyaml.Yaml

import java.io.{BufferedReader, InputStreamReader}
import java.util.stream.Collectors
import java.util.Map
import scala.collection.mutable.ArrayBuffer

class Wall(val direction: Direction, val xPos: Int, val zPos: Int)

/**
 * Stage represents the physical world in the game world.
 */
class Stage {
  // Load and parse world file
  private val worldFileSource = getClass.getResourceAsStream("/world.yml")
  private val worldFile = new Yaml().load[Map[String, Any]](worldFileSource)
  // Spawn point
  private val spawnPointMap = worldFile.get("spawn").asInstanceOf[Map[String, Any]]
  private val spawnPoint = (
    spawnPointMap.get("x").asInstanceOf[Int],
    spawnPointMap.get("y").asInstanceOf[Int],
  )
  // Parse stage string
  private val stageString = worldFile.get("stage").asInstanceOf[String]
  private val stageGrid = stageString.split("\n").map(_.split("\\s+"))
  val height: Int = stageGrid.length
  val width: Int = stageGrid.head.length
  // Walls
  private val (horizontalWalls, verticalWalls) = generateWalls()
  private val lightPositions = findLights()
  private val demonPositions = findDemons()
  private val cubePositions = findCubes()

  def getWallPositions: (Array[Array[Option[Wall]]], Array[Array[Option[Wall]]]) =
    (horizontalWalls, verticalWalls)

  def getAllAvailablePositions: Array[(Int, Int)] = {
    val positions = ArrayBuffer[(Int, Int)]()
    for (y <- 0 until height) {
      for (x <- 0 until width) {
        if stageGrid(y)(x) != "X" then {
          positions += ((x, y))
        }
      }
    }
    positions.toArray
  }

  def getLightPositions: Array[(Int, Int, Boolean)] = lightPositions
  def getDemonPositions: Array[(Int, Int)] = demonPositions
  def getCubePositions: Array[(Int, Int)] = cubePositions
  def getSpawnPoint: (Int, Int) = spawnPoint

  private def loopThroughWalls(f: (Int, Int) => Unit): Unit = {
    for (y <- 0 until height) {
      for (x <- 0 until width) {
        f(x, y)
      }
    }
  }

  private def generateWalls(): (Array[Array[Option[Wall]]], Array[Array[Option[Wall]]]) = {
    // There are always grid size + 1 walls
    val horizontalWalls = Array.fill[Option[Wall]](height + 1, width + 1)(None)
    val verticalWalls = Array.fill[Option[Wall]](height + 1, width + 1)(None)

    // Returns true if there is a wall at the given position
    def gridHasWallAt(x: Int, y: Int): Boolean = {
      try {
        if y < 0 || y >= height || x < 0 || x >= width then false
        else stageGrid(y)(x) == "X"
      } catch {
        case _: ArrayIndexOutOfBoundsException =>
          throw new RuntimeException(
            s"Stage configuration invalid: Could not parse tile at (x = $x, y = $y)",
          )
      }
    }

    loopThroughWalls((x, y) => {
      if gridHasWallAt(x, y) then {
        // Only add a wall if the neighbouring position doesn't have a wall
        Array(1, -1).foreach(d => {
          if !gridHasWallAt(x + d, y) then {
            val xPos = x + (d + 1) / 2
            val yPos = y
            verticalWalls(yPos)(xPos) =
              Option(Wall(if d == 1 then Direction.East else Direction.West, xPos, yPos))
          }
          if !gridHasWallAt(x, y + d) then {
            val xPos = x
            val yPos = y + (d + 1) / 2
            horizontalWalls(yPos)(xPos) =
              Option(Wall(if d == 1 then Direction.South else Direction.North, xPos, yPos))
          }
        })
      }
    })
    (horizontalWalls, verticalWalls)
  }

  private def findObjects(
      identifiers: Array[String] = Array(),
      filter: (Int, Int) => Boolean = null,
  ): Array[(Int, Int)] = {
    def objectAt = {
      if filter == null then
        (x: Int, y: Int) => {
          if y < 0 || y >= height || x < 0 || x >= width then false
          else identifiers.contains(stageGrid(y)(x))
        }
      else filter
    }

    val objects = ArrayBuffer[(Int, Int)]()

    loopThroughWalls((x, y) => {
      if objectAt(x, y) then {
        val position: (Int, Int) = (x, y)
        objects += position
      }
    })
    objects.toArray
  }

  private def findDemons(): Array[(Int, Int)] = findObjects(Array("d"))
  private def findCubes(): Array[(Int, Int)] = findObjects(Array("c"))
  private def findLights(): Array[(Int, Int, Boolean)] =
    findObjects(Array("l", "L")).map((x, y) => (x, y, stageGrid(y)(x) == "l"))

  private def squareOverlapsWall(
      wallPos: (Int, Int),
      squarePos: (Float, Float),
      size: Float,
      direction: String,
  ): Boolean = {
    val dx = squarePos(0) - wallPos(0)
    val dz = squarePos(1) - wallPos(1)
    if direction == "horizontal" then {
      dx > -size && dx < 1 + size && dz > -size && dz < size
    } else {
      dx > -size && dx < size && dz > -size && dz < 1 + size
    }
  }

  def canBeInPosition(pos: (Float, Float), size: Float): Boolean = {
    val xPos = math.floor(pos(0)).toInt
    val zPos = math.floor(pos(1)).toInt
    val wallsToCheck =
      Array(
        (0, 0, "horizontal"),
        (0, 0, "vertical"),
        (1, 0, "horizontal"),
        (1, 0, "vertical"),
        (0, 1, "horizontal"),
        (0, 1, "vertical"),
        (1, 1, "horizontal"),
        (1, 1, "vertical"),
        (-1, 0, "horizontal"),
        (-1, 1, "horizontal"),
        (0, -1, "vertical"),
        (1, -1, "vertical"),
      );
    !wallsToCheck.exists(wall => {
      val wallX = xPos + wall(0)
      val wallZ = zPos + wall(1)
      val walls = if wall(2) == "horizontal" then horizontalWalls else verticalWalls
      if wallX >= 0 && wallZ >= 0 && wallX < walls.head.length && wallZ < walls.length
      then {
        val wallExists: Boolean = walls(wallZ)(wallX).isDefined
        wallExists && squareOverlapsWall((wallX, wallZ), pos, size, wall(2))
      } else false
    })
  }
}
