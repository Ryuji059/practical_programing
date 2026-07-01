package jp.ac.gifu_u.info.katsuya.prog;

import org.osmdroid.util.GeoPoint;
import java.util.ArrayList;

public class RoadSegment {

    // 道路の種類
    public RoadType type;

    // 線を構成する点
    public ArrayList<GeoPoint> points;

    public RoadSegment(RoadType type) {
        this.type = type;
        this.points = new ArrayList<>();
    }
}