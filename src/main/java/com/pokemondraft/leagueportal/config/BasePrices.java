package com.pokemondraft.leagueportal.config;

import java.util.*;

final class BasePrices {
  private BasePrices() {}

  static Map<String,Integer> values() {
    Map<String,Integer> out = new LinkedHashMap<>();
    String data = """
20:Rillaboom|Sneasler|Incineroar|Salamence|Indeedee (Female)|Kingambit|Basculegion (Male)|Garchomp|Golisopod|Gholdengo|Archaludon|Pelipper|Milotic
19:Raichu|Farigiraf|Charizard|Gardevoir|Sylveon|Arcanine (Hisuian)|Tyranitar|Whimsicott|Armarouge|Staraptor|Metagross|Indeedee (Male)
18:Sinistcha|Volcarona|Torkoal|Excadrill|Floette (Eternal Flower)|Politoed|Grimmsnarl|Lucario|Swampert|Froslass|Gengar|Baxcalibur|Dragonite
17:Ninetales (Alolan)|Aerodactyl|Venusaur|Sableye|Primarina|Glimmora|Corviknight|Pawmot|Blastoise|Delphox|Annihilape|Hatterene
16:Absol|Maushold|Dragapult|Talonflame|Ceruledge|Kommo-o|Camerupt|Hydreigon|Blaziken|Typhlosion (Hisuian)|Mawile|Sirfetch'd|Vivillon
15:Gallade|Inteleon|Zoroark (Hisuian)|Meowscarada|Tsareena|Rotom Wash|Alakazam|Kleavor|Empoleon|Espathra|Weavile|Chandelure
14:Scovillain|Mimikyu|Malamar|Aegislash|Pincurchin|Scizor|Grapploct|Clefable|Toxapex|Toxtricity (Amped)|Cinderace|Kangaskhan|Meganium
13:Goodra (Hisuian)|Basculegion (Female)|Greninja|Oranguru|Gyarados|Mamoswine|Samurott (Hisuian)|Overqwil|Snorlax|Scrafty|Altaria|Rotom Heat
12:Bellibolt|Araquanid|Toxtricity (Low Key)|Pyroar|Lopunny|Drampa|Lycanroc (Dusk)|Sceptile|Tinkaton|Klefki|Meowstic (Female)|Vanilluxe|Ninetales
11:Meowstic (Male)|Starmie|Slowking (Galarian)|Jolteon|Arcanine|Clawitzer|Aggron|Umbreon|Skarmory|Skeledirge|Gliscor|Spiritomb
10:Hawlucha|Azumarill|Audino|Ampharos|Eelektross|Steelix|Slowbro|Abomasnow|Crabominable|Raichu (Alolan)|Ditto|Heliolisk|Slowbro (Galarian)
9:Houndoom|Serperior|Houndstone|Alcremie|Noivern|Golurk|Palafin|Dragalge|Krookodile|Infernape|Chesnaught|Feraligatr
8:Cofagrigus|Hippowdon|Conkeldurr|Manectric|Wyrdeer|Espeon|Perrserker|Salazzle|Arboliva|Glaceon|Mudsdale|Vileplume
7:Decidueye (Hisuian)|Wigglytuff|Reuniclus|Mr. Mime|Persian (Alolan)|Vaporeon|Rhyperior|Torterra|Heracross|Medicham|Aurorus|Typhlosion
6:Scolipede|Machamp|Runerigus|Mr. Rime|Tauros (Paldean Blaze)|Pikachu|Glalie|Beedrill|Pidgeot|Aromatisse|Banette|Quaquaval|Tauros (Paldean Aqua)
5:Liepard|Hydrapple|Slowking|Morpeko|Toxicroak|Orthworm|Garganacl|Squawkabilly|Sharpedo|Zoroark|Tyrantrum
4:Rampardos|Gogoat|Mabosstiff|Slurpuff|Thievul|Pangoro|Lycanroc (Midday)|Emboar|Victreebel|Florges|Chimecho|Goodra|Trevenant|Farfetch'd
3:Leafeon|Beartic|Rotom Frost|Musharna|Avalugg (Hisuian)|Toucannon|Swalot|Luxray|Rotom Mow|Falinks|Bastiodon
2:Diggersby|Roserade|Gourgeist|Pinsir|Polteageist|Emolga|Flareon|Arbok|Barbaracle|Dedenne|Decidueye|Flapple|Passimian
1:Castform|Persian|Sandaconda|Stunfisk|Forretress|Ariados|Appletun|Qwilfish|Lycanroc (Midnight)|Furfrou|Rotom Fan|Avalugg
0:Garbodor|Simipour|Tauros|Stunfisk (Galarian)|Rotom|Simisage|Simisear|Watchog|Tauros (Paldean Combat)|Samurott
""";
    for (String line : data.strip().split("\n")) {
      String[] pair = line.split(":", 2);
      int price = Integer.parseInt(pair[0]);
      for (String name : pair[1].split("\\|")) out.putIfAbsent(name, price);
    }
    return Collections.unmodifiableMap(out);
  }
}
