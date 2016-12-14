package edu.mit.media.funf;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class BuildConfigHelper {


    private static final String BUILD_CONFIG = "com.arcticahealth.arctica.models.BuildConfigHelper";

    public static final boolean DEBUG = getDebug();
    public static final String AWS_ACCOUNT_ID = (String) getBuildConfigValue("AWS_ACCOUNT_ID");
    public static final String COGNITO_POOL_ID = (String) getBuildConfigValue("COGNITO_POOL_ID");
    public static final String COGNITO_UNAUTH_ROLE_ARN = (String) getBuildConfigValue("COGNITO_UNAUTH_ROLE_ARN");
    public static final String COGNITO_AUTH_ROLE_ARN = (String) getBuildConfigValue("COGNITO_AUTH_ROLE_ARN");
    public static final String COGNITO_POOL_REGION = (String) getBuildConfigValue("COGNITO_POOL_REGION");
    public static final String S3_BUCKET_NAME = (String) getBuildConfigValue("S3_BUCKET_NAME");
    public static final String S3_KMS_KEY = (String) getBuildConfigValue("S3_KMS_KEY");
    public static final String GOOGLE_WEB_CLIENT_ID = (String) getBuildConfigValue("GOOGLE_WEB_CLIENT_ID");
    public static final String DB_APP_CONFIG = (String) getBuildConfigValue("DB_APP_CONFIG");
    public static final String DB_SURVEY_ANSWERS = (String) getBuildConfigValue("DB_SURVEY_ANSWERS");
    public static final String DB_MOOD_RATINGS = (String) getBuildConfigValue("DB_MOOD_RATINGS");
    public static final String DB_PERSONS = (String) getBuildConfigValue("DB_PERSONS");
    public static final String DYNAMO_POOL_REGION = (String) getBuildConfigValue("DYNAMO_POOL_REGION");



    private static boolean getDebug() {
        Object o = getBuildConfigValue("DEBUG");
        if (o != null && o instanceof Boolean) {
            return (Boolean) o;
        } else {
            return false;
        }
    }

    @Nullable
    private static Object getBuildConfigValue(String fieldName) {
        try {
            Class c = Class.forName(BUILD_CONFIG);
            Field f = c.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
