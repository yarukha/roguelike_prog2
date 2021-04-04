package game

import enemy._
import entity._
import item._
import weapon._
import map._
import position._
import graphics._
import messageHandler._
import scalafx.scene.input.KeyCode
import json._

object Game
{
    val player = new Player()
    val cursor = new Cursor(GameWindow.contextGame)
    var currentPhase = ""
    var currentWeapon = player.weapon

    var enemiesVector:Vector[Enemy] = Vector()

    def eventHandler(kc:KeyCode) =
    {
      kc.getName match
      {
        case "Right" | "Left" | "Up" | "Down" => handleArrow(kc.getName)
        case "A"      => setPhase("attack")
        case "I"      => setPhase("info")
        case "Space"  => handleSelection()
        case "Esc"    => setPhase("move")
        case "E"      => setPhase("inventory")
        case "F"      => player.inventory.drop()
        case "G"      => pickUp()
        case "Enter"  => loop()
        case "F1"     => MessageHandler.setHelp()
        case _        => ()
      }
    }

    def setPhase(phase:String) = 
    {
        cursor.visible = true
        var selectionPhase = true // Are we selcting a tile on the map
        if(phase == "move")
        {
            Map.setHighlight((p:Point)=>(player.pos.distance(p) <= player.curAP))
            cursor.limitation = true
        }
        else if(phase == "attack")
        {
            setAttackHighlight()
            val p = Map.findHighlight()
            if(p.x == -1) // if no solution is found
            {
                cursor.visible = false
            }
            else
            {
                cursor.setPos(p)
            }
            cursor.limitation = true
        }
        else if(phase == "info")
        {
            cursor.limitation = false // cursor can move freely on all visible tiles
            Map.setHighlight((p:Point)=>false)
        }
        else if(phase == "inventory")
        {
          selectionPhase = false
        }
        if(selectionPhase && phase != currentPhase)
        {
            if(!Map.fromPoint(cursor.pos).isHighlighted())
            {
              cursor.setPos(player.pos)
            }
            if(currentWeapon.zone != Zones.classic _) // other weapon zone should have the cursor on the player's tile
            {
              cursor.setPos(player.pos)
            }
        }
        currentPhase = phase
    }

    def handleSelection() =
    {
        currentPhase match
        {
            case "move"   => player.move(cursor.pos)
                             setPhase("move")
                             //MessageHandler.clear()

            case "attack" => MessageHandler.clear()
                             player.attack(cursor.pos)
                             setPhase("move")

            case "info"   => ()
            case "inventory" => player.inventory.useItem()
            case _ => println(currentPhase)
      }
    }

    def handleArrow(event:String):Unit = 
    {
      if(currentPhase == "inventory")
      {
        event match
        {
          case "Right"  => player.inventory.nextPage()
          case "Left"   => player.inventory.prevPage()
          case "Up"     => player.inventory.moveItem(-1)
          case "Down"   => player.inventory.moveItem(1)
        }
      }
      else
      {
        event match
        {
          case "Right"  => cursor.rotate(1)
          case "Left"   => cursor.rotate(-1)
          case "Up"     => cursor.move(cursor.getDir(1))
          case "Down"   => cursor.move(cursor.getDir(-1))
        }
      }
      if(currentPhase == "attack")
      {
        // update attack zone when rotation
        setAttackHighlight()
      }

    }

    def setAttackHighlight():Unit =
    {

        // Zones.classic is different because it attack only on tile, but we need to select which one
        if (currentWeapon.zone == "classic")
        {
          Map.setHighlight((p:Point)=>p.distance(player.pos) >= currentWeapon.innerRange && p.distance(player.pos) <= currentWeapon.outerRange, true)
        }
        else
          Map.setHighlight((p:Point)=>currentWeapon.getZone()(currentWeapon.innerRange, currentWeapon.outerRange, cursor.currentDir, player.pos, p), true)
    }

    def initialization() =
    {
        // generate map : already done for now
        player.move(new Point(0, 0))
        player.inventory.add(WeaponCreator.create())
        player.inventory.add(WeaponCreator.create("Fire Ball"))
        player.inventory.add(WeaponCreator.create("sword"))
        player.inventory.add(new Bandages)

        setPhase("move")
        MessageHandler.clear()
        player.inventory.display()
        player.inventory.curInv = 0

        // creating and placing enemies :
        enemiesVector = Map.getEnemies()

        // creating and placing items :
    }

    def loop() = 
    {
        player.endTurn()
        player.inventory.display()

        changeWeapon(player.weapon)

        enemiesVector = enemiesVector.filter(_.curHP > 0) // We remove enemies killed by the player
        enemiesVector.foreach
        { e =>
            e.curAP = e.baseAP + e.modifAP
            e.IA()
        }
        // We separate in case we add animation to display the damage done
        enemiesVector.foreach
        {
          e => e.applyEffects()
        }
        enemiesVector.foreach
        {
          e => e.endTurn()
        }
        enemiesVector = enemiesVector.filter(_.curHP > 0) // We remove enemies dying of other causes than the player

        setPhase(currentPhase)    // ensure highlight is up to date

        if(player.curHP <= 0)
        {
          // for now on game over, the game is just reset
          player.curHP = player.maxHP
          initialization()
        }
        Map.update()  // We update the rooms of the map
        player.displayInfo() // We update the text on screen to update the player's status
    }

    def changeWeapon(weapon:Weapon):Unit=
    {
      currentWeapon = weapon
      setPhase(currentPhase)
    }

    def pickUp() =
    {
      Map.fromPoint(player.pos).item match
      {
        case None    => ()
        case Some(i) => if (player.curWeight + i.weight <= player.maxWeight)
                        {
                          player.inventory.add(i)
                          Map.fromPoint(player.pos).item = None
                        }
      }
    }
}
