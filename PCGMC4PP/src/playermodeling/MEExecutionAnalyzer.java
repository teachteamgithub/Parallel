package playermodeling;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pmutils.Pair;
import pmutils.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class MEExecutionAnalyzer {

    public static HashMap<String, String> colors;

    private static final Logger logger = LogManager.getLogger(MEExecutionAnalyzer.class);

    public static void setColorMap() {
        colors = new HashMap<>();
        String[] color_strings = {" ", "!", "\"", "#", "$", "%", "&", "\'", "(", ")", "/", "."};
        for (int i = 0; i < color_strings.length; i++) {
            colors.put(color_strings[i], Integer.toString(i));
        }
    }

    public static void printBoard(ArrayList< ArrayList<String> > board) {
        for ( int i = 0; i < board.size(); i++ ) {
            logger.debug(String.join(" ", board.get(i)));
        }
    }

    public static LevelData analyzeMEExecution(String meExecutionFilepath, String criticalSectionRootPath, SkillAnalyzer skillAnalyzer) {
        logger.info("---------------------------------- Analyzing Model Execution Output! ----------------------------------");

        Gson gson = new Gson();
        LevelData levelData = new LevelData();

        /* Specific component information */
        ArrayList<String> diverterIDs = new ArrayList<String>();
        ArrayList<String> semaphoreIDs = new ArrayList<String>();

        ArrayList<String> buttonIDs = new ArrayList<String>();
        ArrayList<String> deliveryIDs = new ArrayList<String>();
        ArrayList<String> pickupIDs = new ArrayList<String>();

        HashMap<Integer, Integer> componentIDToNumDelivered = new HashMap<Integer, Integer>();
        HashMap<Integer, Integer> componentIDToNumMissed = new HashMap<Integer, Integer>();
        HashMap<Integer, Integer> componentIDToNumDeliveryRequired = new HashMap<Integer, Integer>();
        HashMap<Integer, String> componentIDToCondition = new HashMap<Integer, String>();

        /* Rule: Understand that arrows move at unpredictable rates */
        HashMap<String, Pair<Integer, Integer>> prevSemaphoreLocations = null;
        HashMap<String, String> prevSemaphoreTracks = null;

        HashMap<String, Pair<Integer, Integer>> curLocSemaphores = new HashMap<>();
        HashMap<String, String> curTrackSemaphores = new HashMap<>();

        HashMap<String, String> buttonComponentMap = new HashMap<>();
        HashMap<String, ArrayList<String>> componentButtonMap = new HashMap<>();

        boolean criticalSectionDataPopulated = false;
        LinkedHashMap<String, LinkedHashMap<String, ArrayList<Pair<Integer, Integer>>>> criticalSectionData = null;
        try {
            BufferedReader br = new BufferedReader(new FileReader(meExecutionFilepath));
            String meExecutionFileLine;
            int meExecutionNumLines = 0;
            int boardWidth, boardHeight = 0;
            boolean startComponentParsing = false;
            boolean startExecutionParsing = false;
            boolean startSkillListParsing = false;
            while ((meExecutionFileLine = br.readLine()) != null) {

                if (meExecutionFileLine.startsWith("board_width")) {
                    boardWidth = Integer.parseInt(meExecutionFileLine.split("\t")[1]);
                    levelData.data.put("width", boardWidth);
                    logger.trace("Board width: " + boardWidth);
                }

                if (meExecutionFileLine.startsWith("board_height")) {
                    boardHeight = Integer.parseInt(meExecutionFileLine.split("\t")[1]);
                    levelData.data.put("height", boardHeight);
                    logger.trace("Board height: " + boardHeight);
                }

                if (meExecutionFileLine.startsWith("SKILLS")) {
                    startSkillListParsing = true;
                    logger.info("Reading skill list!");
                } else {
                    if (startSkillListParsing) {
                        if (meExecutionFileLine.length() == 0) {
                            startSkillListParsing = false;
                            logger.debug("-----------------------------------------------------------");

                        } else {
                            ArrayList<String> skillsPerLevel = (ArrayList<String>) levelData.data.get("skills_per_level");
                            logger.debug("Skill: " + meExecutionFileLine.trim());

                            skillsPerLevel.add(meExecutionFileLine.trim());
                            levelData.data.put("skills_per_level", skillsPerLevel);
                        }
                    }
                }


                if (meExecutionFileLine.startsWith("DIRECTIONS")) {
                    logger.info("Reading direction map!");
                    ArrayList<ArrayList<String>> directionBoard = (ArrayList<ArrayList<String>>) levelData.data.get("direction_layout");
                    for (int i = meExecutionNumLines + 1; i < meExecutionNumLines + boardHeight + 1; i++) {
                        String tmp = br.readLine();
                        ArrayList<String> row = new ArrayList<String>();
                        for (int m = 0; m < tmp.length(); m++) {
                            row.add(String.valueOf(tmp.charAt(m)));
                        }
                        directionBoard.add(row);
                        levelData.data.replace("direction_layout", directionBoard);
                    }
                    logger.debug("----- Printing out direction board! -----");
                    printBoard(directionBoard);
                    logger.debug("-----------------------------------------");
                }

                if (meExecutionFileLine.startsWith("COLORS")) {
                    logger.info("Reading colors map!");
                    ArrayList<ArrayList<String>> color = (ArrayList<ArrayList<String>>) levelData.data.get("color_layout");
                    for (int i = meExecutionNumLines + 1; i < meExecutionNumLines + boardHeight + 1; i++) {
                        String tmp = br.readLine();
                        ArrayList<String> row = new ArrayList<String>();
                        for (int m = 0; m < tmp.length(); m++) {
                            row.add(String.valueOf(tmp.charAt(m)));
                        }
                        color.add(row);
                    }
                    levelData.data.replace("color_layout", color);
                    logger.debug("----- Printing out color board! -----");
                    printBoard(color);
                    logger.debug("-----------------------------------------");
                }

                if (meExecutionFileLine.startsWith(("level_id"))) {
                    int levelID = Integer.parseInt(meExecutionFileLine.split("\t")[1]);
                    String levelFilename;
                    if (levelID < 10) {
                        levelFilename = String.format("level0%d.txt", levelID);
                    } else {
                        levelFilename = String.format("level%d.txt", levelID);
                    }
                    logger.info("Reading critical section data: " + levelFilename);

                    /* Read in critical section information */
                    if (new File(criticalSectionRootPath + "/" + levelFilename).exists() && !criticalSectionDataPopulated) {
                        criticalSectionDataPopulated = true;
                        criticalSectionData = new LinkedHashMap<>();
                        try {
                            BufferedReader criticalSectionFileBufferedReader =
                                    new BufferedReader(new FileReader(criticalSectionRootPath + "/" + levelFilename));
                            String criticalSectionFileLine;
                            int criticalSectionFileNumLines = 0;
                            int boardHeightLocal = 0;
                            ArrayList<ArrayList<String>> directionBoard = new ArrayList<>();
                            while ((criticalSectionFileLine = criticalSectionFileBufferedReader.readLine()) != null) {

                                if (criticalSectionFileLine.startsWith("board_height")) {
                                    boardHeightLocal = Integer.parseInt(criticalSectionFileLine.split("\t")[1]);
                                    logger.debug("Board height: " + boardHeightLocal);
                                }

                                if (criticalSectionFileLine.startsWith("DIRECTIONS")) {
                                    logger.info("Reading direction map in critical section file!");
                                    for (int i = criticalSectionFileNumLines + 1; i < criticalSectionFileNumLines + boardHeightLocal + 1; i++) {
                                        String tmp = criticalSectionFileBufferedReader.readLine();
                                        ArrayList<String> row = new ArrayList<String>();
                                        for (int m = 0; m < tmp.length(); m++) {
                                            row.add(String.valueOf(tmp.charAt(m)));
                                        }
                                        directionBoard.add(row);
                                    }
                                    logger.debug("----- Printing out direction board! -----");
                                    printBoard(directionBoard);
                                    logger.debug("-----------------------------------------");
                                }

                                if (criticalSectionFileLine.startsWith("CRITICALSECTIONS")) {
                                    logger.info("Reading critical section map in critical section file!");
                                    ArrayList<ArrayList<String>> criticalSection = (ArrayList<ArrayList<String>>) levelData.data.get("critical_section");
                                    for (int i = criticalSectionFileNumLines + 1; i < criticalSectionFileNumLines + boardHeightLocal + 1; i++) {
                                        String tmp = criticalSectionFileBufferedReader.readLine();
                                        ArrayList<String> row = new ArrayList<String>();
                                        for (int m = 0; m < tmp.length(); m++) {
                                            row.add(String.valueOf(tmp.charAt(m)));
                                        }
                                        criticalSection.add(row);
                                    }
                                    logger.debug("----- Printing out critical section board! -----");
                                    printBoard(criticalSection);
                                    logger.debug("------------------------------------------------");
                                    levelData.data.replace("critical_section", criticalSection);
                                }

                                criticalSectionFileNumLines++;
                            }
                            ArrayList<ArrayList<String>> criticalSection = (ArrayList<ArrayList<String>>) levelData.data.get("critical_section");
                            logger.info("Analyzing critical section data!");
                            for (int i = 0; i < criticalSection.size(); i++) {
                                for (int j = 0; j < criticalSection.get(0).size(); j++) {
                                    logger.debug(String.format("(i,j): (%d,%d) - %s", i, j, criticalSection.get(i).get(j)));

                                    if (Character.isUpperCase(criticalSection.get(i).get(j).charAt(0))) {
                                        /* Delivery Point */
                                        if (!criticalSectionData.containsKey(criticalSection.get(i).get(j).toLowerCase())) {
                                            LinkedHashMap<String, ArrayList<Pair<Integer, Integer>>> coords =
                                                    new LinkedHashMap<String, ArrayList<Pair<Integer, Integer>>>();
                                            coords.put("semaphore", new ArrayList<Pair<Integer, Integer>>());
                                            coords.put("button", new ArrayList<Pair<Integer, Integer>>());
                                            criticalSectionData.put(criticalSection.get(i).get(j).toLowerCase(), coords);
                                        }
                                        LinkedHashMap<String, ArrayList<Pair<Integer, Integer>>> tmp = criticalSectionData.get(criticalSection.get(i).get(j).toLowerCase());
                                        ArrayList<Pair<Integer, Integer>> coords = tmp.get("button");
                                        if (i - 1 >= 0 && directionBoard.get(i - 1).get(j).equals("A")) {
                                            coords.add(new Pair<Integer, Integer>(j, i - 1));
                                        }

                                        if (i + 1 < directionBoard.size() && directionBoard.get(i + 1).get(j).equals("V")) {
                                            coords.add(new Pair<Integer, Integer>(j, i + 1));
                                        }

                                        if (j - 1 >= 0 && directionBoard.get(i).get(j - 1).equals("<")) {
                                            coords.add(new Pair<Integer, Integer>(j - 1, i));
                                        }

                                        if (j + 1 < directionBoard.get(0).size() && directionBoard.get(i).get(j + 1).equals(">")) {
                                            coords.add(new Pair<Integer, Integer>(j + 1, i));
                                        }
                                        tmp.replace("button", coords);
                                        criticalSectionData.replace(criticalSection.get(i).get(j).toLowerCase(), tmp);
                                    }

                                    if (Character.isLowerCase(criticalSection.get(i).get(j).charAt(0))) {
                                        /* Pickup Point */
                                        if (!criticalSectionData.containsKey(criticalSection.get(i).get(j).toLowerCase())) {
                                            LinkedHashMap<String, ArrayList<Pair<Integer, Integer>>> coords =
                                                    new LinkedHashMap<String, ArrayList<Pair<Integer, Integer>>>();
                                            coords.put("semaphore", new ArrayList<Pair<Integer, Integer>>());
                                            coords.put("button", new ArrayList<Pair<Integer, Integer>>());
                                            criticalSectionData.put(criticalSection.get(i).get(j).toLowerCase(), coords);
                                        }
                                        LinkedHashMap<String, ArrayList<Pair<Integer, Integer>>> tmp = criticalSectionData.get(criticalSection.get(i).get(j).toLowerCase());
                                        ArrayList<Pair<Integer, Integer>> coords = tmp.get("semaphore");

                                        if (i - 1 >= 0 && directionBoard.get(i - 1).get(j).equals("V")) {
                                            coords.add(new Pair<Integer, Integer>(j, i - 1));
                                        }

                                        if (i + 1 < directionBoard.size() && directionBoard.get(i + 1).get(j).equals("A")) {
                                            coords.add(new Pair<Integer, Integer>(j, i + 1));
                                        }

                                        if (j - 1 >= 0 && directionBoard.get(i).get(j - 1).equals(">")) {
                                            coords.add(new Pair<Integer, Integer>(j - 1, i));
                                        }

                                        if (j + 1 < directionBoard.get(0).size() && directionBoard.get(i).get(j + 1).equals("<")) {
                                            coords.add(new Pair<Integer, Integer>(j + 1, i));
                                        }
                                        tmp.replace("semaphore", coords);
                                        criticalSectionData.replace(criticalSection.get(i).get(j).toLowerCase(), tmp);
                                    }
                                }
                            }
                            logger.debug("Level: " + levelFilename);
                            for (String section : criticalSectionData.keySet()) {
                                for (String sem_or_button : criticalSectionData.get(section).keySet()) {
                                    for (Pair<Integer, Integer> coord : criticalSectionData.get(section).get(sem_or_button)) {
                                        logger.debug("Section %s, Component %s, Coordinate (i,j): (%d,%d)", section, sem_or_button, coord.p1, coord.p2);
                                    }
                                }
                            }
                            criticalSectionFileBufferedReader.close();
                        } catch ( IOException e ) {
                            logger.fatal("Unable to read critical section file: " + criticalSectionRootPath + "/" + levelFilename);
                            logger.catching(Level.FATAL, e);
                        }
                    }
                }

                if (meExecutionFileLine.startsWith("goal_struct")) {
                    logger.info("Analyzing goal struct in ME Execution file");
                    /* Rule: "Understand specific delivery points" and "Deliver packages" */

                    HashMap<String, Object> goalInformation = gson.fromJson(meExecutionFileLine.split("\t")[1], HashMap.class);

                    /* For each required goal, check the number of packages each delivery point requires */
                    ArrayList<LinkedTreeMap<String, Object>> required = (ArrayList<LinkedTreeMap<String, Object>>) goalInformation.get("required");
                    for (LinkedTreeMap<String, Object> cond : required) {

                        /* NOTE: We may need to consider other types of goals */
                        if (!(cond.get("type")).equals("delivery")) {
                            /* Anything but delivery component */
                            continue;
                        }

                        int componentID = ((Double) cond.get("id")).intValue();
                        int numDeliveriesRequired = ((Double) cond.get("value")).intValue();
                        String condition = (String) cond.get("condition");

                        /* Set up id -> num_delivered mapping */
                        componentIDToNumDelivered.put(componentID, 0);
                        componentIDToNumMissed.put(componentID, 0);
                        componentIDToNumDeliveryRequired.put(componentID, numDeliveriesRequired);
                        componentIDToCondition.put(componentID, condition);
                    }
                }

                if (meExecutionFileLine.startsWith("EXECUTION")) {
                    startExecutionParsing = true;
                    logger.info("Reading execution data!");
                } else {
                    if (startExecutionParsing) {
                        if (meExecutionFileLine.length() == 0) {
                            startExecutionParsing = false;
                            logger.debug("-----------------------------------------------------------");
                        } else {
                            String [] splitLine = meExecutionFileLine.split("\t");
                            if (splitLine[0].equals("D")) {
                                /* Execution denotes a package was delivered */
                                HashMap<String, Object> deliveryExecution = gson.fromJson(meExecutionFileLine.split("\t")[5], HashMap.class);
                                /* Structure example:
                                    {"missed_items":[],"delivered_items":[2002],"delivered_to":3001}
                                    {exchange_between_b=1004.0, exchange_between_a=1001.0}
                                */
                                if (!deliveryExecution.containsKey("delivered_items")) {
                                    /* This is an exchange point */
                                    if (deliveryExecution.containsKey("exchange_between_b")) {
                                        /* Check if both have packages. If both do, then exchange. NOTE: Is it possible to exchange with a thread that doesn't have a package? */
                                        if (deliveryExecution.get("exchange_between_a") != null && deliveryExecution.get("exchange_between_b") != null) {
                                            skillAnalyzer.updateRuleEvidence("Understand exchange points");
                                        }
                                    } else {
                                        continue;
                                    }
                                } else {
                                    ArrayList<Integer> delivered = (ArrayList<Integer>) deliveryExecution.get("delivered_items");
                                    ArrayList<Integer> missed = (ArrayList<Integer>) deliveryExecution.get("missed_items");
                                    logger.debug("Delivery execution: " + deliveryExecution.toString());

                                    int id = ((Double) deliveryExecution.get("delivered_to")).intValue();
                                    if (componentIDToNumMissed.containsKey(id)) {
                                        componentIDToNumMissed.replace(id, componentIDToNumMissed.get(id) + missed.size());
                                        componentIDToNumDelivered.replace(id, componentIDToNumDelivered.get(id) + delivered.size());
                                    }
                                }
                            } else {
                                if ( splitLine[0].equals("E") ) {
                                    HashMap<String, Object> parsedInfo = Utils.parseComponentInformation(splitLine[5]);
                                    logger.info("Parsed Execution Data: " + parsedInfo);
                                    if ( parsedInfo.containsKey("race_condition_detected") ) {
                                        logger.debug("Running check to see if player avoided race condition");
                                        if ( parsedInfo.get("race_condition_detected").equals("false") ) {
                                            logger.debug("Race condition successfully avoided");
                                            skillAnalyzer.updateRuleEvidence("Avoid race conditions");
                                        } else {
                                            logger.debug("Race condition not successfully avoided");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (meExecutionFileLine.startsWith("COMPONENTS")) {
                    logger.info("Reading component information!");
                    startComponentParsing = true;
                } else {
                    if (startComponentParsing) {
                        if (meExecutionFileLine.length() == 0) {
                            startComponentParsing = false;
                            logger.debug("-----------------------------------------------------------");
                        } else {
                            String[] componentInfo = meExecutionFileLine.split("\t");

                            /* Component -> x,y coordinate on board */
                            if (levelData.data.containsKey("comp_loc_map")) {
                                HashMap<String, ArrayList<Integer>> compLocationMap =
                                        (HashMap<String, ArrayList<Integer>>) levelData.data.get("comp_loc_map");
                                ArrayList<Integer> tmp = new ArrayList<Integer>();
                                tmp.add(Integer.parseInt(componentInfo[2]));
                                tmp.add(Integer.parseInt(componentInfo[3]));
                                compLocationMap.put(componentInfo[0], tmp);
                                levelData.data.replace("comp_loc_map", compLocationMap);
                            }

                            /* Actual information about the component is located at index 6 */
                            HashMap<String, Object> parsedInfo = Utils.parseComponentInformation(componentInfo[6]);

                            if (parsedInfo.containsKey("color")) {
                                if (levelData.data.containsKey("comp_color_map")) {
                                    HashMap<String, String> compColorMap =
                                            (HashMap<String, String>) levelData.data.get("comp_color_map");
                                    int x = Integer.parseInt(componentInfo[2]);
                                    int y = Integer.parseInt(componentInfo[3]);
                                    ArrayList<ArrayList<String>> colorBoard =
                                            (ArrayList<ArrayList<String>>) levelData.data.get("color_layout");
                                    String colorID = colors.get(colorBoard.get(y).get(x));
                                    if (colorID == null) {
                                        logger.warn("Add this color to the list: " + colorBoard.get(y).get(x));
                                    } else {
                                        compColorMap.put(componentInfo[0], colorID);
                                    }
                                    levelData.data.replace("comp_color_map", compColorMap);
                                }
                            }
                            logger.debug(String.format("%s (ID=%s): (%s,%s)", componentInfo[1], componentInfo[0], componentInfo[2], componentInfo[3]));
                            switch (componentInfo[1]) {
                                case "diverter":

                                    diverterIDs.add(componentInfo[0]);
                                    break;
                                case "delivery":
                                    deliveryIDs.add(componentInfo[0]);
                                    break;
                                case "pickup":
                                    pickupIDs.add(componentInfo[0]);
                                    break;
                                case "semaphore":
                                    if ( componentInfo[4].equals("P") ) {
                                        skillAnalyzer.updateRuleEvidence("Place objects on the track");
                                    }
                                    semaphoreIDs.add(componentInfo[0]);
                                    ArrayList<ArrayList<String>> colorBoard =
                                            (ArrayList<ArrayList<String>>) levelData.data.get("color_layout");
                                    if (!curLocSemaphores.containsKey(componentInfo[0])) {
                                        int x = Integer.parseInt(componentInfo[2]);
                                        int y = Integer.parseInt(componentInfo[3]);
                                        curLocSemaphores.put(componentInfo[0], new Pair<Integer, Integer>(x, y));
                                        String trackColor = colors.get(colorBoard.get(y).get(x));
                                        curTrackSemaphores.put(componentInfo[0], trackColor);
                                        logger.debug(String.format("Track of semaphore %s, (x,y): (%d,%d)", trackColor, x, y));
                                    } else {
                                        int x = Integer.parseInt(componentInfo[2]);
                                        int y = Integer.parseInt(componentInfo[3]);
                                        curLocSemaphores.replace(componentInfo[0], new Pair<Integer, Integer>(x, y));

                                        String trackColor = colors.get(colorBoard.get(y).get(x));
                                        curTrackSemaphores.replace(componentInfo[0], trackColor);
                                        logger.debug(String.format("Track of semaphore %s, (x,y): (%d,%d)", trackColor, x, y));
                                    }
                                    break;
                                case "signal":
                                    if ( componentInfo[4].equals("P") ) {
                                        skillAnalyzer.updateRuleEvidence("Place objects on the track");
                                    }
                                    if (parsedInfo.containsKey("link") && ((Double) parsedInfo.get("link")).intValue() != -1) {
                                        String linkedItemID = Integer.toString(((Double) parsedInfo.get("link")).intValue());
                                        buttonComponentMap.put(componentInfo[0], linkedItemID);
                                        if (componentButtonMap.containsKey(linkedItemID)) {
                                            ArrayList<String> buttons = componentButtonMap.get(linkedItemID);
                                            buttons.add(componentInfo[0]);
                                            componentButtonMap.replace(linkedItemID, buttons);
                                        } else {
                                            ArrayList<String> buttons = new ArrayList<String>();
                                            buttons.add(componentInfo[0]);
                                            componentButtonMap.put(linkedItemID, buttons);
                                        }
                                        buttonIDs.add(componentInfo[0]);
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
                meExecutionNumLines++;
            }
            br.close();
        } catch (IOException e) {
            logger.fatal("Unable to read ME Execution file: " + meExecutionFilepath);
            logger.catching(Level.FATAL, e);
        }


        logger.info("Checking whether player understood the skill \"Understand that arrows move at unpredictable rates\"");

        /* Rule: "Understand that arrows move at unpredictable rates"  */
        if (prevSemaphoreLocations == null) {
            prevSemaphoreLocations = new HashMap<>();
            prevSemaphoreTracks = new HashMap<>();
            for (String semID : curTrackSemaphores.keySet()) {
                prevSemaphoreTracks.put(semID, curTrackSemaphores.get(semID));
            }
            for (String semID : curLocSemaphores.keySet()) {
                prevSemaphoreLocations.put(semID, curLocSemaphores.get(semID));
            }
        } else {
            for (String semID : curTrackSemaphores.keySet()) {
                String curTrackColor = curTrackSemaphores.get(semID);
                if (!prevSemaphoreTracks.containsKey(semID)) {
                    /* Semaphore was deleted. */
                    continue;
                }
                String prevTrackColor = prevSemaphoreTracks.get(semID);

                if (prevTrackColor.equals(curTrackColor)) {
                    /* Check for significant change */
                    Pair<Integer, Integer> prevSemaphoreCoord = prevSemaphoreLocations.get(semID);
                    Pair<Integer, Integer> curSemaphoreCoord = curLocSemaphores.get(semID);

                    int manhattanDistance = Math.abs(prevSemaphoreCoord.p1 - curSemaphoreCoord.p1) + Math.abs(prevSemaphoreCoord.p2 - curSemaphoreCoord.p2);
                    if (!(prevSemaphoreCoord.equals(curSemaphoreCoord)) && (manhattanDistance > 1)) {
                        /* Changed "significantly" */
                        skillAnalyzer.updateRuleEvidence("Understand that arrows move at unpredictable rates");
                    }
                }
            }
            prevSemaphoreTracks.clear();
            prevSemaphoreLocations.clear();
            for (String semID : curTrackSemaphores.keySet()) {
                prevSemaphoreTracks.replace(semID, curTrackSemaphores.get(semID));
            }
            for (String semID : curLocSemaphores.keySet()) {
                prevSemaphoreLocations.replace(semID, curLocSemaphores.get(semID));
            }
        }

        HashMap<String, String> buttonSemaphoreLinkMap = new HashMap<String, String>();
        HashMap<String, ArrayList<String>> semaphoreButtonLinkMap = new HashMap<String, ArrayList<String>>();

        logger.info("Checking whether player understood the skills \"Be able to link semaphores to buttons\"");
        /* Rule: "Be able to link semaphores to buttons" and "Understand that arrows move at unpredictable rates" */
        for (String semID : componentButtonMap.keySet()) {
            if (semaphoreIDs.contains(semID)) {
                skillAnalyzer.updateRuleEvidence("Be able to link semaphores to buttons");
                for (String buttonID : componentButtonMap.get(semID)) {
                    buttonSemaphoreLinkMap.put(buttonID, semID);
                }
                semaphoreButtonLinkMap.put(semID, componentButtonMap.get(semID));
            }
        }

        ArrayList<String> unlinkedSemaphoreIDs = new ArrayList<String>();
        for (String semID : semaphoreIDs) {
            if (!semaphoreButtonLinkMap.containsKey(semID)) {
                /* This semaphore is not linked */
                unlinkedSemaphoreIDs.add(semID);
            }
        }

        logger.info("Checking whether player understood the skills \"Understand that arrows move at unpredictable rates\"");

        HashMap<String, String> colorMap = (HashMap<String, String>) levelData.data.get("comp_color_map");
        HashMap<String, ArrayList<Integer>> compLocMap =
                (HashMap<String, ArrayList<Integer>>) levelData.data.get("comp_loc_map");
        HashMap<String, Integer> numSemaphoresUnlinkedPerTrack = new HashMap<String, Integer>();
        for (String semID : unlinkedSemaphoreIDs) {
            String color = colorMap.get(semID);
            if (numSemaphoresUnlinkedPerTrack.containsKey(color)) {
                numSemaphoresUnlinkedPerTrack.replace(color, numSemaphoresUnlinkedPerTrack.get(color) + 1);
            } else {
                numSemaphoresUnlinkedPerTrack.put(color, 1);
            }
        }

        for (String track : numSemaphoresUnlinkedPerTrack.keySet()) {
            if (numSemaphoresUnlinkedPerTrack.get(track) < 2) {
                skillAnalyzer.updateRuleEvidence("Understand that arrows move at unpredictable rates");
            }
        }

        /* Check "Alternating access with semaphores and locks (ensure mutual exclusion)" rule here
         * TODO future work: As of now, critical section and ensure mutual exclusion are the same rule-wise. Update the rule.
         * Maybe we need to consider the color of track...
         * */


        logger.info("Checking whether player understood the skill \"Block critical sections\"");
        if (criticalSectionData != null) {
            for (String section : criticalSectionData.keySet()) {
                ArrayList<Pair<Integer, Integer>> semaphoreCoords = criticalSectionData.get(section).get("semaphore");
                ArrayList<Pair<Integer, Integer>> buttonCoords = criticalSectionData.get(section).get("button");

                ArrayList<Pair<Integer, Integer>> semaphoreCoordsListCopy = new ArrayList<Pair<Integer, Integer>>();
                for (Pair<Integer, Integer> c : semaphoreCoords) {
                    semaphoreCoordsListCopy.add(c.clone());
                }
                ArrayList<Pair<Integer, Integer>> buttonCoordsListCopy = new ArrayList<Pair<Integer, Integer>>();
                for (Pair<Integer, Integer> c : buttonCoords) {
                    buttonCoordsListCopy.add(c.clone());
                }

                if (buttonCoordsListCopy.size() == semaphoreCoordsListCopy.size()) {
                    for (String button : buttonSemaphoreLinkMap.keySet()) {
                        ArrayList<Integer> buttonCoordAsArrayList = compLocMap.get(button);
                        ArrayList<Integer> semaphoreCoordAsArrayList = compLocMap.get(buttonSemaphoreLinkMap.get(button));

                        if (semaphoreCoordAsArrayList == null) {
                            /* Linking to a non-existant semaphore */
                            continue;
                        }

                        Pair<Integer, Integer> buttonCoord = new Pair<Integer, Integer>(buttonCoordAsArrayList.get(0), buttonCoordAsArrayList.get(1));
                        Pair<Integer, Integer> semaphoreCoord = new Pair<Integer, Integer>(semaphoreCoordAsArrayList.get(0), semaphoreCoordAsArrayList.get(1));

                        if (buttonCoordsListCopy.contains(buttonCoord) && semaphoreCoordsListCopy.contains(semaphoreCoord)) {
                            buttonCoordsListCopy.remove(buttonCoord);
                            semaphoreCoordsListCopy.remove(semaphoreCoord);
                        }
                    }
                    /* If these are not empty, this could mean:
                        1) Haven't blocked the critical section (buttons and semaphores may not be linked or there are missing semaphores and buttons)
                        2) Blocked more than the critical section
                    */
                    if (buttonCoordsListCopy.isEmpty() && semaphoreCoordsListCopy.isEmpty()) {
                        skillAnalyzer.updateRuleEvidence("Block critical sections");
                    }
                } else {
                    if (buttonCoordsListCopy.size() > semaphoreCoordsListCopy.size()) {
                        for (String semaphore : semaphoreButtonLinkMap.keySet()) {
                            ArrayList<Integer> semaphoreCoordAsArrayList = compLocMap.get(semaphore);
                            if (semaphoreCoordAsArrayList == null) {
                                continue;
                            }
                            Pair<Integer, Integer> semaphoreCoord = new Pair<Integer, Integer>(semaphoreCoordAsArrayList.get(0), semaphoreCoordAsArrayList.get(1));
                            if (semaphoreCoordsListCopy.contains(semaphoreCoord)) {
                                /* Now, search through all buttons connected to it and make sure all are in the critical section */
                                boolean allButtonsAccounted = true;
                                for (String button : semaphoreButtonLinkMap.get(semaphore)) {
                                    ArrayList<Integer> buttonCoordAsArrayList = compLocMap.get(button);
                                    Pair<Integer, Integer> buttonCoord = new Pair<Integer, Integer>(buttonCoordAsArrayList.get(0), buttonCoordAsArrayList.get(1));
                                    if (buttonCoordsListCopy.contains(buttonCoord)) {
                                        buttonCoordsListCopy.remove(buttonCoord);
                                    } else {
                                        /* This is a problem because a thread could unlock the critical section from outside */
                                        allButtonsAccounted = false;
                                        break;
                                    }
                                }
                                if (allButtonsAccounted) {
                                    semaphoreCoordsListCopy.remove(semaphoreCoord);
                                }
                            }
                        }
                        if (buttonCoordsListCopy.isEmpty() && semaphoreCoordsListCopy.isEmpty()) {
                            skillAnalyzer.updateRuleEvidence("Block critical sections");
                        }
                    }
                }
            }
        }

        logger.info("Checking whether player understood the skill \"Synchronize multiple arrows\"");
        for (String buttonID : buttonSemaphoreLinkMap.keySet()) {
            String button1Color = colorMap.get(buttonID);
            /* Check the color of the linked semaphore */
            String semaphore2Color = colorMap.get(buttonSemaphoreLinkMap.get(buttonID));
            logger.debug("Button 1 color: " + button1Color + ", Semaphore 2 color: " + semaphore2Color);

            /* Make sure these colors are different */
            if (button1Color.equals(semaphore2Color)) {
                continue;
            }

            /* Check if a semaphore exists on lock1_color, and check what lock it's connected to.  */
            for (String componentID : colorMap.keySet()) {
                /* If one the same track as lock1, and is a semaphore */
                if (colorMap.get(componentID).equals(button1Color) && semaphoreIDs.contains(componentID) && !unlinkedSemaphoreIDs.contains(componentID)) {
                    for (String button : semaphoreButtonLinkMap.get(componentID)) {
                        String button2Color = colorMap.get(button);
                        /* Check if lock2's color is the same as sem2's color, and check if lock2 isn't the same color as sem1 */
                        logger.debug("Button 2 color: " + button2Color + ", Semaphore 2 color: " + semaphore2Color);
                        if (button2Color.equals(semaphore2Color)) {
                            skillAnalyzer.updateRuleEvidence("Synchronize multiple arrows");
                        }
                    }
                }
            }
        }

        logger.info("Checking whether player understood the skills \"Deliver packages\" and \"Understand specific delivery points\"");
        /* NOTE: Is this computed for each file sent over to the ME or for all executions? */
        int totalMissed = 0;
        boolean allPackagesDelivered = true;
        for (Integer id : componentIDToNumDeliveryRequired.keySet()) {
            String condition = componentIDToCondition.get(id);
            int value = componentIDToNumDelivered.get(id);
            int component_value = componentIDToNumDeliveryRequired.get(id);
            int missed = componentIDToNumMissed.get(id);
            totalMissed += missed;

            if ("eq".equals(condition) && !(value == component_value)) {
                allPackagesDelivered = false;
                break;
            } else if ("lt".equals(condition) && !(value < component_value)) {
                allPackagesDelivered = false;
                break;
            } else if ("gt".equals(condition) && !(value > component_value)) {
                allPackagesDelivered = false;
                break;
            } else if ("ne".equals(condition) && !(value != component_value)) {
                allPackagesDelivered = false;
                break;
            }
        }
        if (allPackagesDelivered) {
            skillAnalyzer.updateRuleEvidence("Deliver packages");
        }

        if (totalMissed == 0) {
            skillAnalyzer.updateRuleEvidence("Understand specific delivery points");
        }

        return levelData;
    }
}