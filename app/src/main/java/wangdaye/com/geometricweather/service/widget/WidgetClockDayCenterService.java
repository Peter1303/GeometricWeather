package wangdaye.com.geometricweather.service.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.RemoteViews;

import com.baidu.location.LocationClient;

import wangdaye.com.geometricweather.data.database.MyDatabaseHelper;
import wangdaye.com.geometricweather.data.database.WeatherTable;
import wangdaye.com.geometricweather.data.model.Location;
import wangdaye.com.geometricweather.data.model.Weather;
import wangdaye.com.geometricweather.ui.activity.MainActivity;
import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.receiver.widget.WidgetClockDayCenterProvider;
import wangdaye.com.geometricweather.utils.LocationUtils;
import wangdaye.com.geometricweather.utils.TimeUtils;
import wangdaye.com.geometricweather.utils.WeatherUtils;
import wangdaye.com.geometricweather.utils.WidgetAndNotificationUtils;

/**
 * Widget clock day center service.
 * */


public class WidgetClockDayCenterService extends Service
        implements LocationUtils.OnRequestLocationListener, WeatherUtils.OnRequestWeatherListener {
    // widget
    private LocationClient client;

    // data
    private int startId;
    private Location location;
    private static final int REQUEST_CODE = 1;

    /** <br> life cycle. */

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.client = new LocationClient(this);
        this.startId = startId;

        SharedPreferences sharedPreferences = this.getSharedPreferences(
                getString(R.string.sp_widget_clock_day_center_setting), Context.MODE_PRIVATE);
        this.location = new Location(
                sharedPreferences.getString(
                        getString(R.string.key_location),
                        getString(R.string.local)));

        int[] widgetIds = AppWidgetManager.getInstance(this)
                .getAppWidgetIds(new ComponentName(this, WidgetClockDayCenterProvider.class));
        if (widgetIds != null && widgetIds.length != 0) {
            if (location.location.equals(getString(R.string.local))) {
                LocationUtils.requestLocation(client, this);
            } else {
                WeatherUtils.requestWeather(this, location, location.location, true, this);
            }
            this.setAlarmIntent();
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        client.stop();
    }

    /** <br> widget. */

    public static void refreshWidgetView(Context context, Weather weather) {
        if (weather == null) {
            return;
        }

        // get settings & time.
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                context.getString(R.string.sp_widget_clock_day_center_setting),
                Context.MODE_PRIVATE);
        boolean showCard = sharedPreferences.getBoolean(context.getString(R.string.key_show_card), false);
        boolean blackText = sharedPreferences.getBoolean(context.getString(R.string.key_black_text), false);
        boolean hideRefreshTime = sharedPreferences.getBoolean(context.getString(R.string.key_hide_refresh_time), false);
        boolean isDay = TimeUtils.getInstance(context).getDayTime(context, false).isDay;

        // get text color.
        int textColor;
        if (blackText || showCard) {
            textColor = ContextCompat.getColor(context, R.color.colorTextDark);
        } else {
            textColor = ContextCompat.getColor(context, R.color.colorTextLight);
        }

        // get remote views.
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_clock_day_center);

        // build view.
        int[] imageId = WeatherUtils.getWeatherIcon( // get icon resource id.
                weather.weatherKindNow,
                isDay);
        views.setImageViewResource( // set icon.
                R.id.widget_clock_day_center_icon,
                imageId[3]);
        // build weather & temps text.
        String[] texts = WidgetAndNotificationUtils.buildWidgetDayStyleText(weather);
        views.setTextViewText( // set weather.
                R.id.widget_clock_day_center_weather,
                texts[0]);
        views.setTextViewText( // set temps.
                R.id.widget_clock_day_center_temp,
                texts[1]);
        views.setTextViewText( // set time.
                R.id.widget_clock_day_center_refreshTime,
                weather.location + "." + weather.refreshTime);
        // set text color.
        views.setTextColor(R.id.widget_clock_day_center_clock, textColor);
        views.setTextColor(R.id.widget_clock_day_center_weather, textColor);
        views.setTextColor(R.id.widget_clock_day_center_temp, textColor);
        views.setTextColor(R.id.widget_clock_day_center_refreshTime, textColor);
        // set card visibility.
        views.setViewVisibility(R.id.widget_clock_day_center_card, showCard ? View.VISIBLE : View.GONE);
        // set refresh time visibility.
        views.setViewVisibility(R.id.widget_clock_day_center_refreshTime, hideRefreshTime ? View.GONE : View.VISIBLE);
        // set clock intent.
        Intent intentClock = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        PendingIntent pendingIntentClock = PendingIntent.getActivity(context, 0, intentClock, 0);
        views.setOnClickPendingIntent(R.id.widget_clock_day_center_clockButton, pendingIntentClock);
        // set weather intent.
        Intent intentWeather = new Intent(context, MainActivity.class);
        PendingIntent pendingIntentWeather = PendingIntent.getActivity(context, 0, intentWeather, 0);
        views.setOnClickPendingIntent(R.id.widget_clock_day_center_weatherButton, pendingIntentWeather);

        // commit.
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        appWidgetManager.updateAppWidget(
                new ComponentName(context, WidgetClockDayCenterProvider.class),
                views);
    }

    /** <br> data. */

    private void setAlarmIntent() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent target = new Intent(getBaseContext(), WidgetClockDayCenterService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                getBaseContext(),
                REQUEST_CODE,
                target,
                PendingIntent.FLAG_UPDATE_CURRENT);

        int duration = 1000 * 60 * 60 * WidgetAndNotificationUtils.getWidgetRefreshHours(this);
        alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + duration,
                pendingIntent);
    }

    /** <br> interface. */

    // request location.

    @Override
    public void requestLocationSuccess(String locationName) {
        WeatherUtils.requestWeather(this, location, locationName, true, this);
    }

    @Override
    public void requestLocationFailed() {
        LocationUtils.simpleLocationFailedFeedback(this);
        refreshWidgetView(this,
                WeatherTable.readWeather(
                        MyDatabaseHelper.getDatabaseHelper(this),
                        location.location));
        this.stopSelf(startId);
    }

    // request weather.

    @Override
    public void requestWeatherSuccess(Location result, boolean isDaily) {
        refreshWidgetView(this, result.weather);
        this.stopSelf(startId);
    }

    @Override
    public void requestWeatherFailed(Location result, boolean isDaily) {
        WidgetAndNotificationUtils.refreshWidgetFailed(this);
        refreshWidgetView(this,
                WeatherTable.readWeather(
                        MyDatabaseHelper.getDatabaseHelper(this),
                        location.location));
        this.stopSelf(startId);
    }
}