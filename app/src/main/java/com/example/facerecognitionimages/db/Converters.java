package com.example.facerecognitionimages.db;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class Converters {
    @TypeConverter
    public static float[] fromString(String value) {
        Type listType = new TypeToken<float[]>() {}.getType();
        return new Gson().fromJson(value, listType);
    }

    @TypeConverter
    public static String fromArray(float[] list) {
        Gson gson = new Gson();
        return gson.toJson(list);
    }
}
