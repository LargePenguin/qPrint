# qPrint
The fastest map printer in the west.
Written by Chubby__Penguin.

Works best for flat carpet and fullblock staircased maparts. Staircased carpet is not supported due to some internal nonsense with Baritone.

# Features

* **97% automated map printing.** The only thing you have to do for yourself is seal the completed map.
* **TPS & Hang recovery.** If Baritone gets stuck, qPrint will employ various mechanisms to get it un-stuck.
* **Auto-restock.** When your account runs out of materials, qPrint will automatically grab what it needs from your chests.
  * If there's no space in your inventory, qPrint will make use of a user-defined trash chest to create space.
* **Multi-agent avoidance.** You can work on the same print with multiple accounts, and the bots will never start placing blocks over each other (if it can be avoided)

# Quickstart

## Installation
- Install Fabric 1.21.1.
- Download Meteor Client and put it in your mods folder.
- Clone the repo and build with your favorite Java IDE or download the latest prebuilt jar [here](https://github.com/LargePenguin/qPrint/releases/latest).
- Place qPrint in your mods folder with Meteor.
- Profit.

## Configuration

**Recommended:** Download the official carpet duper schematic [here](https://github.com/LargePenguin/qPrint/releases/latest).

The default configuration works (relatively) well on Constantiam.net, which is where I tend to play. If you're using qPrint on another server, you might have to change some settings.

## Positioning

* Open a blank map to make sure you're at the upper-left corner of a map.
* Use F3 + G to show chunk borders.
* If you're using the official schematic, paste it so that the light blue wool is in the top-left corner of the map
![Screenshot 0](http://raw.githubusercontent.com/LargePenguin/qPrint/refs/heads/main/screenshots/Pasted%20image%2020250601204713.png)
### Labeling

qPrint uses item frames to determine what chests it should expect to find which items in. One type of item per chest, please!

If you're not using the official schematic, you'll need to put an item frame on each chest containing the type of item within that chest. Make sure that when you define your chest region, the item frames as well as the chests are contained within the bounds.

qPrint uses an item frame containing a cactus to denote a trash chest. When excess materials need to be dumped, this is where they will go. For the official schematic, I've left it up to the end user to determine what should happen to items placed in the trash chest.
## Define a chest region

In the module settings, enable "Render Volumes".
![Screenshot 1](http://raw.githubusercontent.com/LargePenguin/qPrint/refs/heads/main/screenshots/Pasted%20image%2020250601204850.png)

Use the commands `.qp loc set chestP1 [pos]` and `.qp loc set chestP2 [pos]` to define a storage region.

If you're using the official schematic, run one command while standing on the green wool and the other while standing on the red wool.
![Screenshot 2](http://raw.githubusercontent.com/LargePenguin/qPrint/refs/heads/main/screenshots/Pasted%20image%2020250601205459.png)

**Tip:** Verify that your region contains all of your chests by using the "Print State Contents" option in the module settings.
![Screenshot 3](http://raw.githubusercontent.com/LargePenguin/qPrint/refs/heads/main/screenshots/Pasted%20image%2020250601210350.png)
## Define the platform origin

Stand at the top left-most block rendered on your map and run the command `.qp loc set platformOrigin [pos]`.

For the official schematic, run this command while standing on the light blue wool.
![Screenshot 4](http://raw.githubusercontent.com/LargePenguin/qPrint/refs/heads/main/screenshots/Pasted%20image%2020250601205758.png)

## Run your first print

Place mapart schematics in your `schematics/` folder.
qPrint wraps around Baritone's builder process, so your schematic must be in a format Baritone can read (basically anything but .nbt)

Start your print by running the command `.qp print <your_schematic>`.
And that's about it!

# Command Details

The primary command, `.qp`, is documented below.

* `.qp loc [clear|get|list|set] [locationName] <coords>`: gets, sets, or lists the specified location. Valid `locationName` are: `chestP1`, `chestP2`, and `platformOrigin`
* `.qp print [schematic]`: Prints the given schematic onto your platform.
* `.qp [pause|resume|cancel|stop]`: Pauses, resumes, or cancels the print.
* `.qp eta`: Prints the estimated time remaining on the print.
	* **Note:** Servers with a render distance smaller than the size of your platform will result in the time estimate being downright abysmal in its accuracy.

# FAQ

* **Is this backdoored?**
	* No.
	* Look at the source code for yourself.
	* Always build from source. If you use prebuilt jar files from random anarchy players, you're a fool and will eventually suffer for it.
* **Why is the ETA so terrible?**
	* Servers with low render distance prevent qPrint from accurately measuring overall print progress.
	* This will be addressed in a future release.
* **I got kicked for unlikely inventory interactions, why?**
	* qPrint probably tried to move stuff around too quickly while restocking itself. 
	* Try playing around with the "Max Clicks Per Tick", "Container Interact Delay", "Container Close Delay", and "AutoSteal Delay" options in the module.
* **Why does qPrint get stuck standing next to my chests?**
	* Most servers will verify that your character actually has direct line of sight to blocks you interact with.
	* Try enabling the "Swing Hand When Opening" and "Face Container When Opening" options in the module.
   	* Dropping the maximum reach in the module settings may also increase the odds of a successful chest interaction.
	* Use the official duper schematic. The restock chests are all positioned in such a way as to maximize the odds of qPrint successfully interacting with the chests.
* **Baritone prints a material list, but qPrint doesn't run the restock routine**
  	* Make sure you have the qPrint module enabled in the modules list. 
