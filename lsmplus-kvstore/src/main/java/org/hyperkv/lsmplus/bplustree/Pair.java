package org.hyperkv.lsmplus.bplustree;

public class Pair<Left, Right> {
    public final Left left;
    public final Right right;

    Pair(Left left, Right right) {
        this.left = left;
        this.right = right;
    }

    public static <Left, Right> Pair<Left, Right> of(Left left, Right right) {
        return new Pair<>(left, right);
    }

    public Left first() {
        return left;
    }

    public Right second() {
        return right;
    }
}
