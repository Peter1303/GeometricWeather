package wangdaye.com.geometricweather.Activity;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Message;
import android.os.StrictMode;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.Poi;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import wangdaye.com.geometricweather.Data.GsonResult;
import wangdaye.com.geometricweather.Data.JuheWeather;
import wangdaye.com.geometricweather.Data.Location;
import wangdaye.com.geometricweather.Data.MyDatabaseHelper;
import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.Receiver.WidgetProviderDay;
import wangdaye.com.geometricweather.Service.WidgetService;
import wangdaye.com.geometricweather.Widget.HandlerContainer;
import wangdaye.com.geometricweather.Widget.SafeHandler;

/**
 * Created by WangDaYe on 2016/2/8.
 */

public class CreateWidgetDayActivity extends Activity implements HandlerContainer{
    // widget
    private ImageView imageViewCard;
    private TextView textViewWeatherNow;
    private TextView textViewTempNow;

    //data
    private List<Location> locationList;
    private String locationName;
    private boolean showCard = false;

    private MyDatabaseHelper databaseHelper;

    private GsonResult gsonResult;

    private final int REFRESH_DATA_SUCCEED = 1;
    private final int REFRESH_DATA_FAILED = 0;

    // baidu location
    public LocationClient mLocationClient = null;
    public BDLocationListener myListener = new MyLocationListener();

    // handler
    private SafeHandler<CreateWidgetDayActivity> safeHandler;

    //TAG
//    private final String TAG = "CreateWidgetDayActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        setContentView(R.layout.activity_create_widget_day);
    }

    @Override
    protected void onStart() {
        super.onStart();

        this.safeHandler = new SafeHandler<>(this);

        this.locationName = getString(R.string.local);

        ImageView imageViewWall = (ImageView) this.findViewById(R.id.create_widget_day_wall);
        imageViewWall.setImageDrawable(WallpaperManager.getInstance(CreateWidgetDayActivity.this).getDrawable());

        RelativeLayout relativeLayoutWidgetContainer = (RelativeLayout) this.findViewById(R.id.widget_day) ;
        this.imageViewCard = (ImageView) relativeLayoutWidgetContainer.findViewById(R.id.widget_day_card);

        this.textViewWeatherNow = (TextView) relativeLayoutWidgetContainer.findViewById(R.id.widget_day_weather);
        this.textViewTempNow = (TextView) relativeLayoutWidgetContainer.findViewById(R.id.widget_day_temp);

        this.initDatabaseHelper();
        this.readLocation();
        if (locationList.size() < 1) {
            locationList.add(new Location(getString(R.string.local)));
        }
        String[] items = new String[locationList.size()];
        for (int i = 0; i < locationList.size(); i ++) {
            items[i] = this.locationList.get(i).location;
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_text, items);
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_text);
        Spinner spinnerCity = (Spinner) this.findViewById(R.id.create_widget_day_spinner);
        spinnerCity.setAdapter(spinnerAdapter);
        spinnerCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                locationName = parent.getItemAtPosition(position).toString();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                locationName = getString(R.string.local);
            }
        });

        Switch switchCard = (Switch) this.findViewById(R.id.create_widget_day_switch_card);
        switchCard.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    imageViewCard.setVisibility(View.VISIBLE);
                    showCard = true;
                    textViewTempNow.setTextColor(ContextCompat.getColor(CreateWidgetDayActivity.this, R.color.colorTextDark));
                    textViewWeatherNow.setTextColor(ContextCompat.getColor(CreateWidgetDayActivity.this, R.color.colorTextDark));
                } else {
                    imageViewCard.setVisibility(View.GONE);
                    showCard = false;
                    textViewTempNow.setTextColor(ContextCompat.getColor(CreateWidgetDayActivity.this, R.color.colorTextLight));
                    textViewWeatherNow.setTextColor(ContextCompat.getColor(CreateWidgetDayActivity.this, R.color.colorTextLight));
                }
            }
        });

        final Button buttonDone = (Button) this.findViewById(R.id.create_widget_day_done);
        buttonDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor editor = getSharedPreferences(
                        getString(R.string.sp_widget_day_setting),
                        MODE_PRIVATE
                ).edit();
                editor.putString(getString(R.string.key_location), locationName);
                editor.putBoolean(getString(R.string.key_show_card), showCard);
                editor.apply();

                Intent intent = getIntent();
                Bundle extras = intent.getExtras();
                int appWidgetId = 0;
                if (extras != null) {
                    appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID);
                }

                buttonDone.setText(getString(R.string.first_refresh_widget));
                buttonDone.setEnabled(true);

                refreshUIFromLocalData();
                refreshWidget();

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                setResult(RESULT_OK, resultValue);
                finish();
            }
        });
    }

    private void initDatabaseHelper() {
        this.databaseHelper = new MyDatabaseHelper(CreateWidgetDayActivity.this,
                MyDatabaseHelper.DATABASE_NAME,
                null,
                1);
    }

    private void readLocation() {
        this.locationList = new ArrayList<>();
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        Cursor cursor = database.query(MyDatabaseHelper.TABLE_LOCATION,
                null, null, null, null, null, null);

        if(cursor.moveToFirst()) {
            do {
                String location = cursor.getString(cursor.getColumnIndex(MyDatabaseHelper.COLUMN_LOCATION));
                this.locationList.add(new Location(location));
            } while (cursor.moveToNext());
        }
        cursor.close();
        database.close();
    }

    private void refreshWidget() {
        if(this.locationName.equals(getString(R.string.local))) {
            mLocationClient = new LocationClient(this); // 声明LocationClient类
            mLocationClient.registerLocationListener( myListener ); // 注册监听函数

            this.initBaiduMap();
        } else {
            getWeather(locationName);
        }
    }

    private void getWeather(final String searchLocation) {
        Thread thread=new Thread(new Runnable()
        {
            @Override
            public void run()
            { // TODO Auto-generated method stub
                gsonResult = JuheWeather.getRequest(searchLocation);
                Message message=new Message();
                if (gsonResult == null) {
                    message.what = REFRESH_DATA_FAILED;
                } else {
                    message.what = REFRESH_DATA_SUCCEED;
                }
                safeHandler.sendMessage(message);
            }
        });
        thread.start();
    }

    private void initBaiduMap() {
        // initialize baidu location
        mLocationClient.registerLocationListener( myListener );    //注册监听函数
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Battery_Saving);//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        option.setCoorType("bd09ll");//可选，默认gcj02，设置返回的定位结果坐标系
        option.setScanSpan(0);//可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的
        option.setIsNeedAddress(true);//可选，设置是否需要地址信息，默认不需要
        option.setOpenGps(true);//可选，默认false,设置是否使用gps
        option.setLocationNotify(true);//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        option.setIsNeedLocationDescribe(true);//可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        option.setIsNeedLocationPoiList(true);//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        option.setIgnoreKillProcess(false);//可选，默认false，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认杀死
        option.SetIgnoreCacheException(false);//可选，默认false，设置是否收集CRASH信息，默认收集
        option.setEnableSimulateGps(false);//可选，默认false，设置是否需要过滤gps仿真结果，默认需要
        mLocationClient.setLocOption(option);
        mLocationClient.start();
    }

    private void refreshUI() {
        if(this.gsonResult != null) {
            this.refreshUIFromInternet();
        } else {
            Toast.makeText(this, getString(R.string.refresh_widget_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshUIFromInternet() {
        boolean isDay;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (5 < hour && hour < 19) {
            isDay = true;
        } else {
            isDay = false;
        }
        RemoteViews views = new RemoteViews(this.getPackageName(), R.layout.widget_day);

        GsonResult.WeatherNow weatherNow = this.gsonResult.result.data.realtime.weatherNow;
        String weatherKind = JuheWeather.getWeatherKind(weatherNow.weatherInfo);
        int[] imageId = JuheWeather.getWeatherIcon(weatherKind, isDay);
        views.setImageViewResource(R.id.widget_day_image, imageId[3]);
        String weatherTextNow = weatherNow.weatherInfo
                + "\n"
                + weatherNow.temperature
                + "℃";
        views.setTextViewText(R.id.widget_day_weather, weatherTextNow);
        GsonResult.Weather weatherToday = this.gsonResult.result.data.weather.get(0);
        String weatherTextTemp = weatherToday.info.day.get(2)
                + "°"
                + "\n"
                + weatherToday.info.night.get(2)
                + "°";
        views.setTextViewText(R.id.widget_day_temp, weatherTextTemp);
        String[] timeText = this.gsonResult.result.data.realtime.time.split(":");
        String refreshText = this.gsonResult.result.data.realtime.city_name
                + "."
                + timeText[0]
                + ":"
                + timeText[1];
        views.setTextViewText(R.id.widget_day_time, refreshText);

        if(this.showCard) { // show card
            views.setViewVisibility(R.id.widget_day_card, View.VISIBLE);
            views.setTextColor(R.id.widget_day_weather, ContextCompat.getColor(this, R.color.colorTextDark));
            views.setTextColor(R.id.widget_day_temp, ContextCompat.getColor(this, R.color.colorTextDark));
        } else { // do not show card
            views.setViewVisibility(R.id.widget_day_card, View.GONE);
            views.setTextColor(R.id.widget_day_weather, ContextCompat.getColor(this, R.color.colorTextLight));
            views.setTextColor(R.id.widget_day_temp, ContextCompat.getColor(this, R.color.colorTextLight));
        }

        //Intent intent = new Intent("com.geometricweather.receiver.CLICK_WIDGET");
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.widget_day_button, pendingIntent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetManager.updateAppWidget(new ComponentName(this, WidgetProviderDay.class), views);

        SharedPreferences.Editor editor = getSharedPreferences(
                getString(R.string.sp_widget_day_setting), Context.MODE_PRIVATE).edit();
        editor.putBoolean(getString(R.string.key_saved_data), true);
        editor.putString(getString(R.string.key_weather_kind_today), weatherKind);
        editor.putString(getString(R.string.key_weather_today), weatherTextNow);
        editor.putString(getString(R.string.key_temperature_today), weatherTextTemp);
        editor.putString(getString(R.string.key_city_time), refreshText);
        editor.apply();
    }

    private void refreshUIFromLocalData() {
        SharedPreferences sharedPreferences = this.getSharedPreferences(
                getString(R.string.sp_widget_day_setting), Context.MODE_PRIVATE);
        if (! sharedPreferences.getBoolean(getString(R.string.key_saved_data), false)) {
            return;
        }
        String weatherKindToday = sharedPreferences.getString(getString(R.string.key_weather_kind_today), "阴");
        String weatherToday = sharedPreferences.getString(getString(R.string.key_weather_today), getString(R.string.ellipsis));
        String temperatureToday = sharedPreferences.getString(getString(R.string.key_temperature_today), getString(R.string.ellipsis));
        String cityTime = sharedPreferences.getString(getString(R.string.key_city_time), getString(R.string.wait_refresh));

        boolean isDay;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (5 < hour && hour < 19) {
            isDay = true;
        } else {
            isDay = false;
        }

        RemoteViews views = new RemoteViews(this.getPackageName(), R.layout.widget_day);
        int[] imageId = JuheWeather.getWeatherIcon(weatherKindToday, isDay);
        views.setImageViewResource(R.id.widget_day_image, imageId[3]);
        views.setTextViewText(R.id.widget_day_weather, weatherToday);
        views.setTextViewText(R.id.widget_day_temp, temperatureToday);
        views.setTextViewText(R.id.widget_day_time, cityTime);

        if(this.showCard) { // show card
            views.setViewVisibility(R.id.widget_day_card, View.VISIBLE);
            views.setTextColor(R.id.widget_day_weather, ContextCompat.getColor(this, R.color.colorTextDark));
            views.setTextColor(R.id.widget_day_temp, ContextCompat.getColor(this, R.color.colorTextDark));
        } else { // do not show card
            views.setViewVisibility(R.id.widget_day_card, View.GONE);
            views.setTextColor(R.id.widget_day_weather, ContextCompat.getColor(this, R.color.colorTextLight));
            views.setTextColor(R.id.widget_day_temp, ContextCompat.getColor(this, R.color.colorTextLight));
        }

        //Intent intent = new Intent("com.geometricweather.receiver.CLICK_WIDGET");
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.widget_day_button, pendingIntent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetManager.updateAppWidget(new ComponentName(this, WidgetProviderDay.class), views);
    }

    // inner class
    private class MyLocationListener implements BDLocationListener {
        // baidu location listener
        @Override
        public void onReceiveLocation(BDLocation location) {
            //Receive Location
            String locationName = null;

            StringBuffer sb = new StringBuffer(256);
            sb.append("time : ");
            sb.append(location.getTime());
            if (location.getLocType() == BDLocation.TypeGpsLocation){// GPS定位结果
                sb.append("\naddr : ");
                sb.append(location.getAddrStr());
                sb.append("\ndescribe : ");
                sb.append("gps定位成功");
                locationName = location.getCity();
            } else if (location.getLocType() == BDLocation.TypeNetWorkLocation){// 网络定位结果
                sb.append("\naddr : ");
                sb.append(location.getAddrStr());
                sb.append("\ndescribe : ");
                sb.append("网络定位成功");
                locationName = location.getCity();
            } else if (location.getLocType() == BDLocation.TypeOffLineLocation) {// 离线定位结果
                sb.append("\ndescribe : ");
                sb.append("离线定位成功，离线定位结果也是有效的");
                locationName = location.getCity();
            } else if (location.getLocType() == BDLocation.TypeServerError) {
                sb.append("\ndescribe : ");
                sb.append("服务端网络定位失败，可以反馈IMEI号和大体定位时间到loc-bugs@baidu.com，会有人追查原因");
            } else if (location.getLocType() == BDLocation.TypeNetWorkException) {
                sb.append("\ndescribe : ");
                sb.append("网络不同导致定位失败，请检查网络是否通畅");
            } else if (location.getLocType() == BDLocation.TypeCriteriaException) {
                sb.append("\ndescribe : ");
                sb.append("无法获取有效定位依据导致定位失败，一般是由于手机的原因，处于飞行模式下一般会造成这种结果，可以试着重启手机");
            }

            getWeather(locationName);

            sb.append("\nlocationdescribe : ");
            sb.append(location.getLocationDescribe());// 位置语义化信息
            List<Poi> list = location.getPoiList();// POI数据
            if (list != null) {
                sb.append("\npoilist size = : ");
                sb.append(list.size());
                for (Poi p : list) {
                    sb.append("\npoi= : ");
                    sb.append(p.getId() + " " + p.getName() + " " + p.getRank());
                }
            }
            Log.i("BaiduLocationApiDem", sb.toString());
            mLocationClient.stop();
        }
    }

    @Override
    public void handleMessage(Message message) {
        switch(message.what)
        {
            case REFRESH_DATA_SUCCEED:
                refreshUI();
                break;
            default:
                Toast.makeText(this,
                        getString(R.string.refresh_widget_error),
                        Toast.LENGTH_SHORT).show();
                break;
        }
    }
}