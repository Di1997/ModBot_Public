package LevelDBReader;

public class Pair <T1, T2>{
    T1 i1;
    T2 i2;

    public Pair(T1 i1, T2 i2) {
        this.i1 = i1;
        this.i2 = i2;
    }

    public T1 getFirst() {
        return i1;
    }

    public T2 getSecond() {
        return i2;
    }
}
