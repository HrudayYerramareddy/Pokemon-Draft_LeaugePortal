package com.pokemondraft.leagueportal.config;

import com.pokemondraft.leagueportal.model.*;
import com.pokemondraft.leagueportal.repository.*;
import com.pokemondraft.leagueportal.service.ScheduleService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.*;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner seed(AppUserRepository users, TeamRepository teams, PokemonRepository pokemon,
                           LeagueSettingsRepository settings, MatchupRepository matchups, ScheduleService schedule){
        return args -> {
            if(settings.count()==0) settings.save(new LeagueSettings());
            if(teams.count()==0){
                List<Team> saved=new ArrayList<>();
                for(int i=1;i<=16;i++){
                    String div=i<=8?"A":"B";
                    saved.add(teams.save(new Team("Team "+i,"Coach "+i,div,i)));
                }
                users.save(new AppUser("manager","manager",Role.MANAGER,null));
                for(int i=1;i<=16;i++) users.save(new AppUser("coach"+i,"password",Role.COACH,saved.get(i-1).getId()));
            }
            if(pokemon.count()==0){
                Random random=new Random(20260909L);
                for(String name:championsPool()) pokemon.save(new Pokemon(name,random.nextInt(21)));
            } else {
                // Keep an existing demo database in sync when a species is split into
                // competitively distinct forms. Never delete a generic entry if it was drafted.
                Random random=new Random(20260909L);
                for(String name:championsPool()){
                    if(pokemon.findByName(name).isEmpty()) pokemon.save(new Pokemon(name,random.nextInt(21)));
                }
                for(String oldName:List.of("Meowstic","Lycanroc")){
                    pokemon.findByName(oldName).ifPresent(old -> { if(!old.isDrafted()) pokemon.delete(old); });
                }
            }
            if(matchups.count()==0 && teams.count()==16) schedule.generate();
        };
    }

    private List<String> championsPool(){
        return List.of(
            "Venusaur","Charizard","Blastoise","Beedrill","Pidgeot","Arbok","Pikachu","Raichu","Raichu (Alolan)","Clefable",
            "Ninetales","Ninetales (Alolan)","Wigglytuff","Vileplume","Persian","Persian (Alolan)","Arcanine","Arcanine (Hisuian)","Alakazam","Machamp",
            "Victreebel","Slowbro","Slowbro (Galarian)","Farfetch'd","Gengar","Kangaskhan","Starmie","Mr. Mime","Pinsir","Tauros",
            "Tauros (Paldean Combat)","Tauros (Paldean Blaze)","Tauros (Paldean Aqua)","Gyarados","Ditto","Vaporeon","Jolteon","Flareon","Aerodactyl","Snorlax",
            "Dragonite","Meganium","Typhlosion","Typhlosion (Hisuian)","Feraligatr","Ariados","Ampharos","Azumarill","Politoed","Espeon",
            "Umbreon","Slowking","Slowking (Galarian)","Forretress","Steelix","Qwilfish","Scizor","Heracross","Skarmory","Houndoom",
            "Tyranitar","Sceptile","Blaziken","Swampert","Pelipper","Gardevoir","Sableye","Mawile","Aggron","Medicham",
            "Manectric","Swalot","Sharpedo","Camerupt","Torkoal","Altaria","Milotic","Castform","Banette","Chimecho",
            "Absol","Glalie","Salamence","Metagross","Torterra","Infernape","Empoleon","Staraptor","Luxray","Roserade",
            "Rampardos","Bastiodon","Lopunny","Spiritomb","Garchomp","Lucario","Hippowdon","Toxicroak","Abomasnow","Weavile",
            "Rhyperior","Leafeon","Glaceon","Gliscor","Mamoswine","Gallade","Froslass","Rotom","Rotom Heat","Rotom Wash",
            "Rotom Frost","Rotom Fan","Rotom Mow","Serperior","Emboar","Samurott","Samurott (Hisuian)","Watchog","Liepard","Simisage",
            "Simisear","Simipour","Musharna","Excadrill","Audino","Conkeldurr","Scolipede","Whimsicott","Krookodile","Scrafty",
            "Cofagrigus","Garbodor","Zoroark","Zoroark (Hisuian)","Reuniclus","Vanilluxe","Emolga","Eelektross","Chandelure","Beartic",
            "Stunfisk","Stunfisk (Galarian)","Golurk","Hydreigon","Volcarona","Chesnaught","Delphox","Greninja","Diggersby","Talonflame",
            "Vivillon","Pyroar","Floette (Eternal Flower)","Florges","Gogoat","Pangoro","Furfrou","Meowstic (Male)","Meowstic (Female)","Aegislash","Aromatisse",
            "Slurpuff","Malamar","Barbaracle","Dragalge","Clawitzer","Heliolisk","Tyrantrum","Aurorus","Sylveon","Hawlucha",
            "Dedenne","Goodra","Goodra (Hisuian)","Klefki","Trevenant","Gourgeist","Avalugg","Avalugg (Hisuian)","Noivern","Decidueye",
            "Decidueye (Hisuian)","Incineroar","Primarina","Toucannon","Crabominable","Lycanroc (Midday)","Lycanroc (Midnight)","Lycanroc (Dusk)","Toxapex","Mudsdale","Araquanid","Salazzle",
            "Tsareena","Oranguru","Passimian","Golisopod","Mimikyu","Drampa","Kommo-o","Rillaboom","Cinderace","Inteleon",
            "Corviknight","Thievul","Flapple","Appletun","Sandaconda","Toxtricity (Amped)","Toxtricity (Low Key)","Grapploct","Polteageist","Hatterene",
            "Grimmsnarl","Perrserker","Sirfetch'd","Mr. Rime","Runerigus","Alcremie","Falinks","Pincurchin","Indeedee (Male)","Indeedee (Female)",
            "Morpeko","Dragapult","Wyrdeer","Kleavor","Basculegion (Male)","Basculegion (Female)","Sneasler","Overqwil","Meowscarada","Skeledirge",
            "Quaquaval","Pawmot","Maushold","Arboliva","Squawkabilly (Green)","Squawkabilly (Blue)","Squawkabilly (Yellow)","Squawkabilly (White)","Garganacl","Armarouge",
            "Ceruledge","Bellibolt","Mabosstiff","Scovillain","Espathra","Tinkaton","Palafin","Orthworm","Glimmora","Houndstone",
            "Annihilape","Farigiraf","Kingambit","Baxcalibur","Gholdengo","Sinistcha","Archaludon","Hydrapple"
        );
    }
}
