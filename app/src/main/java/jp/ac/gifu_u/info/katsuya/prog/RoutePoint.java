package jp.ac.gifu_u.info.katsuya.prog;

public class RoutePoint {
    //データ管理用のクラス
    public double lat;      // 緯度
    public double lon;      // 経度

    public long time;       // 記録時刻(ms)

    public float speed;     // GPS速度(m/s)

    public double distance; // 開始地点からの累計距離(m)

    public RoutePoint(
            double lat,
            double lon,
            long time,
            float speed,
            double distance
    ){
        this.lat = lat;
        this.lon = lon;
        this.time = time;
        this.speed = speed;
        this.distance = distance;
    }
}
