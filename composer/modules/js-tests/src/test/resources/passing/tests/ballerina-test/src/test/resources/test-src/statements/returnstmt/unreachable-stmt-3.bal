function returnStmtBranch (int value, int b) (int) {
    if (value > 10) {
        return 100;
    } else if (value == 10) {
        return 200;
    } else {
        if (b > 10) {
            return 300;
        } else {
            if (value == 100) {
                return 100;
            } else {
                return -100;
            }
            return 400;
        }
    }
}