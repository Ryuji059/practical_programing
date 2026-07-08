package jp.ac.gifu_u.info.katsuya.prog;

import org.osmdroid.util.GeoPoint;
import java.util.ArrayList;

public class RoadSegment {
    public RoadType type;//道の区分
    public ArrayList<GeoPoint> points;//各点の保存場所
    public String memo;//メモ(危険な理由等を書き込む)

    public RoadSegment(RoadType type) {
        this.type = type;
        this.points = new ArrayList<>();
        this.memo = "";
    }
}