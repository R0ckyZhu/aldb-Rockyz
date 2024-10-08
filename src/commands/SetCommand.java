package commands;

import alloy.AlloyUtils;
import alloy.ParsingConf;
import simulation.DashSimulationManager;
import simulation.SimulationManager;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;

import org.jline.builtins.Completers.FileNameCompleter;
import org.jline.builtins.Completers.TreeCompleter;
import org.jline.reader.Completer;
import org.yaml.snakeyaml.error.YAMLException;

import static org.jline.builtins.Completers.TreeCompleter.node;

public class SetCommand extends Command {
    private final static String CONF_OPTION = "conf";
    private final static String DIFF_OPTION = "diff";

    private final static String ON = "on";
    private final static String OFF = "off";

    public String getName() {
        return CommandConstants.SET_NAME;
    }

    public String getDescription() {
        return CommandConstants.SET_DESCRIPTION;
    }

    public String getHelp() {
        return CommandConstants.SET_HELP;
    }

    public Completer getSpecialCompleter() {
        return new TreeCompleter(
            node(getName(),
                node(CONF_OPTION,
                    node(new FileNameCompleter())),
                node(DIFF_OPTION,
                    node(ON, OFF))));
    }

    public void execute(String[] input, SimulationManager simulationManager) {
        if (input.length < 2 || input.length > 3) {
            System.out.println(getHelp());
            return;
        }

        String option = input[1];

        switch (option) {
            case CONF_OPTION:
                setConf(input, simulationManager);
                break;
            case DIFF_OPTION:
                setDiffMode(input, simulationManager);
                break;
            default:
                System.out.println(getHelp());
        }
    }

    private void setConf(String[] input, SimulationManager simulationManager) {
        // Omitting a filename will set the default conf.
    	if (simulationManager instanceof DashSimulationManager) {
            simulationManager = (DashSimulationManager) simulationManager; 
        } 
        if (input.length < 3) {
            System.out.print(CommandConstants.SETTING_PARSING_OPTIONS);
            simulationManager.setParsingConf(new ParsingConf());
            System.out.println(CommandConstants.DONE);
            return;
        }

        String filename = input[2];
        File file = new File(filename);

        System.out.printf(CommandConstants.SETTING_PARSING_OPTIONS_FROM, filename);

        String inputFileContents;
        try {
            inputFileContents = AlloyUtils.readFromFile(file);
        } catch (IOException e) {
            System.out.println(CommandConstants.FAILED_TO_READ_FILE);
            return;
        }
        try {
            ParsingConf conf = ParsingConf.initializeWithYaml(inputFileContents);
            simulationManager.setParsingConf(conf);
        } catch (YAMLException e) {
            System.out.println(CommandConstants.FAILED_TO_READ_CONF);
            return;
        }
        System.out.println(CommandConstants.DONE);
    }

    private void setDiffMode(String[] input, SimulationManager simulationManager) {
        if (input.length < 3) {
            System.out.println(getHelp());
            return;
        }

        String value = input[2];
        if (value.equals(ON)) {
            simulationManager.setDiffMode(true);
        } else if (value.equals(OFF)) {
            simulationManager.setDiffMode(false);
        } else {
            System.out.println(getHelp());
        }
    }
}
