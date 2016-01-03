package com.stackrating.elo;

import java.util.List;

public class EloCalculator {

    // Compute rating deltas for all players
    public static double[] computeRatingDeltas(List<PlayerInfo> results) {
        return results.stream()
                      .mapToDouble(result -> computeRatingDelta(result, results))
                      .toArray();
    }

    // Compute combined rating deltas (by taking average of pairwise deltas)
    public static double computeRatingDelta(PlayerInfo ownResult, List<PlayerInfo> allResults) {
        return allResults.stream()
                         .filter(pr -> pr != ownResult)
                         .mapToDouble(opponentResult -> eloDelta(ownResult, opponentResult))
                         .average()
                         .orElse(0);  // No opponents => delta = 0
    }

    // Compute single-opponent rating delta
    private static double eloDelta(PlayerInfo ownResult, PlayerInfo opponentResult) {
        double expectedEloScore = 1.0 / (1 + Math.pow(10, (opponentResult.ratingBefore - ownResult.ratingBefore) / 400.0));
        double actualEloScore = eloScore(ownResult.score, opponentResult.score);
        return maxDelta(ownResult.gamesPlayed, opponentResult.gamesPlayed) * (actualEloScore - expectedEloScore);
    }

    // Map my score to a value in range (0..1) which can be used to compare against expected Elo score.
    private static double eloScore(double myScore, double opponentScore) {

        // Better score should always count as complete victory. (Otherwise player with best result
        // may get negative rating delta which seems unfair.)
        if (myScore > opponentScore)
            return 1;

        // Is it a draw? (my score above 99% of opponent score, avoids division by zero below!)
        if (myScore >= .99 * opponentScore)
            return .5;

        // Less than half of opponents score should count as a loss.
        double loss = opponentScore - Math.abs(opponentScore) / 2.0;
        if (myScore < loss)
            return 0;

        // Do a linear interpolation between loss and draw: f(loss) = 0, f(oppScore) = .5
        double k = .5 / (opponentScore - loss);
        double m = .5 - k * opponentScore;
        return k * myScore + m;
    }

    private static double maxDelta(int ownGamesPlayed, int oppGamesPlayed) {
        // "If I've played less than 100 games, my rating should be volatile, otherwise it should
        // be stable, especially if my opponent have played less than 100 games."
        return ownGamesPlayed < 100 ? 8
             : oppGamesPlayed < 100 ?  1
             : 4;
    }

    public static class PlayerInfo {
        int gamesPlayed;
        double ratingBefore;
        double score;

        public PlayerInfo(int gamesPlayed, double ratingBefore, double score) {
            this.gamesPlayed = gamesPlayed;
            this.ratingBefore = ratingBefore;
            this.score = score;
        }
        
        public double getRatingBefore() {
            return ratingBefore;
        }
    }

//    public static void main(String[] args) {
//        List<PlayerResult> results = new ArrayList<>();
//        results.add(new PlayerResult(250, 1700, 35));
//        results.add(new PlayerResult(150, 1600, 30));
//        results.add(new PlayerResult( 10, 1500, 10));
//        results.add(new PlayerResult( 75, 1000, -2));
//        
//        for (int me = 0; me < results.size(); me++) {
//            System.out.print("Player " + me + ":   ");
//            double sum = 0;
//            PlayerResult myResult = results.get(me);
//            for (int opp = 0; opp < results.size(); opp++) {
//                PlayerResult oppResult = results.get(opp);
//                double delta = eloDelta(myResult, oppResult);
//                System.out.printf("  %s%.2f", delta < 0 ? "-" : " ", Math.abs(delta));
//                sum += delta;
//            }
//            System.out.printf("   -> %.2f%n", (double) sum / (results.size()-1));
//        }
//        
//        double[] ratingUpdates = computeRatingDeltas(results);
//        System.out.println(Arrays.toString(ratingUpdates));
//    }
}
