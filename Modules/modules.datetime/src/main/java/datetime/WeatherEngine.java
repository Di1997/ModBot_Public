package datetime;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

public class WeatherEngine {
    static HashMap<Integer, WeatherEngineData> weather = new HashMap<>();

    static {
        WeatherEngineData weather;
        weather = new WeatherEngineData();
        weather.chances.put(Helpers.Weather.Snowy, 40);
        weather.chances.put(Helpers.Weather.Clear, 60);

        WeatherEngine.weather.put(12, weather);
        WeatherEngine.weather.put(1, weather);
        WeatherEngine.weather.put(2, weather);

        weather = new WeatherEngineData();
        weather.processor = WeatherEngine::rainProcessor;

        WeatherEngine.weather.put(3, weather);
        WeatherEngine.weather.put(4, weather);
        WeatherEngine.weather.put(5, weather);
        WeatherEngine.weather.put(6, weather);
        WeatherEngine.weather.put(7, weather);
        WeatherEngine.weather.put(8, weather);
        WeatherEngine.weather.put(9, weather);
        WeatherEngine.weather.put(10, weather);
        WeatherEngine.weather.put(11, weather);
    }

    public static Helpers.Weather getWeather(Helpers.DatetimeData data) {
        return weather.get(data.month).Process(data);
    }

    private static class WeatherEngineData {
        HashMap<Helpers.Weather, Integer> chances = new HashMap<>();
        Function<Helpers.DatetimeData, HashMap<Helpers.Weather, Integer>> processor;

        Helpers.Weather Process(Helpers.DatetimeData data) {
            HashMap<Helpers.Weather, Integer> innerChances = chances;
            if(processor != null)
                innerChances = processor.apply(data);

            if(innerChances.size() == 0)
                throw new IndexOutOfBoundsException(innerChances.size());

            int totalChance = 0;
            int lastChance = -1;
            LinkedHashMap<Helpers.Weather, Integer> weatherList = new LinkedHashMap<>();

            for (Map.Entry<Helpers.Weather, Integer> chanceData: innerChances.entrySet()) {
                totalChance+=chanceData.getValue();
                lastChance+=chanceData.getValue();

                weatherList.put(chanceData.getKey(), lastChance);
            }

            if(totalChance != 100)
                throw new IllegalArgumentException("Weather engine, total chance is not 100!");

            if(innerChances.size() == 1)
                return innerChances.keySet().stream().findFirst().get();

            SecureRandom random = new SecureRandom();
            random.setSeed(random.generateSeed(100));
            int weather = random.nextInt(100);

            lastChance = -1;
            for (Map.Entry<Helpers.Weather, Integer> weatherEntry: weatherList.entrySet()) {
                if(weather > lastChance && weather <= weatherEntry.getValue())
                    return weatherEntry.getKey();

                lastChance+=weatherEntry.getValue();
            }

            return Helpers.Weather.Clear;
        }
    }

    private static HashMap<Helpers.Weather, Integer> rainProcessor(Helpers.DatetimeData data) {
        HashMap<Helpers.Weather, Integer> chanceMap = new HashMap<>();

        if(data.weather == Helpers.Weather.Rainy) {
            SecureRandom random = new SecureRandom();
            random.setSeed(random.generateSeed(100));
            if(random.nextInt(5) == 0) {
                chanceMap.put(Helpers.Weather.Stormy, 100);
                return chanceMap;
            }
        } else if(data.weather == Helpers.Weather.Stormy) {
            chanceMap.put(Helpers.Weather.Clear, 70);
            chanceMap.put(Helpers.Weather.Rainy, 20);
            chanceMap.put(Helpers.Weather.Stormy, 10);

            return chanceMap;
        }

        chanceMap.put(Helpers.Weather.Clear, 70);
        chanceMap.put(Helpers.Weather.Rainy, 30);

        return chanceMap;
    }
}

