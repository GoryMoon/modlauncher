/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 *
 *     Copyright (C) 2017-2019 cpw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.modlauncher;

import cpw.mods.modlauncher.api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static cpw.mods.modlauncher.LogMarkers.*;

/**
 * Identifies the launch target and dispatches to it
 */
class LaunchServiceHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ServiceLoader<ILaunchHandlerService> launchHandlerServices;
    private final Map<String, LaunchServiceHandlerDecorator> launchHandlerLookup;

    public LaunchServiceHandler() {
        launchHandlerServices = ServiceLoaderStreamUtils.errorHandlingServiceLoader(ILaunchHandlerService.class, serviceConfigurationError -> LOGGER.fatal("Encountered serious error loading transformation service, expect problems", serviceConfigurationError));
        launchHandlerLookup = ServiceLoaderStreamUtils.toMap(launchHandlerServices, ILaunchHandlerService::name, LaunchServiceHandlerDecorator::new);
        LOGGER.debug(MODLAUNCHER,"Found launch services [{}]", () -> String.join(",",launchHandlerLookup.keySet()));
    }

    public Optional<ILaunchHandlerService> findLaunchHandler(final String name) {
        return Optional.ofNullable(launchHandlerLookup.getOrDefault(name, null)).map(LaunchServiceHandlerDecorator::getService);
    }
    private void launch(String target, String[] arguments, ITransformingClassLoader classLoader) {
        LOGGER.info(MODLAUNCHER, "Launching target '{}' with arguments {}", target, hideAccessToken(arguments));
        launchHandlerLookup.get(target).launch(arguments, classLoader);
    }

    static List<String> hideAccessToken(String[] arguments) {
        final ArrayList<String> output = new ArrayList<>();
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0 && Objects.equals(arguments[i-1], "--accessToken")) {
                output.add("❄❄❄❄❄❄❄❄");
            } else {
                output.add(arguments[i]);
            }
        }
        return output;
    }

    public void launch(ArgumentHandler argumentHandler, TransformingClassLoader classLoader) {
        String launchTarget = argumentHandler.getLaunchTarget();
        String[] args = argumentHandler.buildArgumentList();
        launch(launchTarget, args, classLoader);
    }

    TransformingClassLoaderBuilder identifyTransformationTargets(final ArgumentHandler argumentHandler) {
        final String launchTarget = argumentHandler.getLaunchTarget();
        final TransformingClassLoaderBuilder builder = new TransformingClassLoaderBuilder();
        Arrays.stream(argumentHandler.getSpecialJars()).forEach(builder::addTransformationPath);
        launchHandlerLookup.get(launchTarget).configureTransformationClassLoaderBuilder(builder);
        return builder;
    }

    void validateLaunchTarget(final ArgumentHandler argumentHandler) {
        if (!launchHandlerLookup.containsKey(argumentHandler.getLaunchTarget())) {
            LOGGER.error(MODLAUNCHER, "Cannot find launch target {}, unable to launch",
                    argumentHandler.getLaunchTarget());
            throw new RuntimeException("Cannot find launch target");
        }
    }
}
