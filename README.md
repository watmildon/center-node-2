# center-node-2

A JOSM plugin that extends the functionality of [center-node](https://github.com/ubipo/center-node) to also allow compacting sets of ways and relations to their individual center points. When replacing sets of closed areas, the tags will be moved to the new node.

For the time being, the plugin will have to be [manually installed](https://wiki.openstreetmap.org/wiki/JOSM/Plugins#Manually_install_JOSM_plugins) by downloading is from the Releases published here or built on your own.

## Usage

Select a set of ways and choose "Replase with Center Node".

Before:
<img width="904" height="907" alt="image" src="https://github.com/user-attachments/assets/ddcbc399-6c96-4b24-8677-a8dcc58b5474" />

After:
<img width="850" height="772" alt="image" src="https://github.com/user-attachments/assets/cd81fa02-bf43-4182-aec5-c0366d022c8f" />


## Caution

The plugin is not written super defensively, it is up to the user to use it in a thoughtful manner. Some things to keep in mind:

 * It works best on layers that are "complete" and have full referrers downloaded
 * For partial layers (ex: from Overpass), it may delete things more aggressively than proper make sure to resolve conflits to "their" versions to keep elements that have referrers that the plugin couldn't see.
