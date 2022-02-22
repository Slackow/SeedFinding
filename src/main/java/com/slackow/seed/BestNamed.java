package com.slackow.seed;

import kaptainwutax.biomeutils.source.BiomeSource;
import kaptainwutax.biomeutils.source.NetherBiomeSource;
import kaptainwutax.featureutils.structure.BastionRemnant;
import kaptainwutax.featureutils.structure.Fortress;
import kaptainwutax.featureutils.structure.Stronghold;
import kaptainwutax.mcutils.rand.ChunkRand;
import kaptainwutax.mcutils.state.Dimension;
import kaptainwutax.mcutils.util.math.DistanceMetric;
import kaptainwutax.mcutils.util.pos.CPos;
import kaptainwutax.seedutils.rand.JRand;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static kaptainwutax.mcutils.version.MCVersion.v1_16_1;

public class BestNamed {
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Input: ");
        String input = (args.length <= 0 ? scanner.nextLine() : args[0]).toLowerCase();
        long comboCount = 1L << input.chars().filter(Character::isLowerCase).count();
        System.out.println("About to search " + comboCount + " seeds, press Enter to confirm.");
        scanner.nextLine();
        scanner.close();
        AtomicInteger a = new AtomicInteger();
        System.out.println("ok");
        SeedInfo.sum = 0;
        var fileOutput = new FileWriter(input + ".txt") {
            @Override
            public void write(String c) throws IOException {
                System.out.print(c);
                super.write(c);
            }
        };

        ExecutorService processor = Executors.newFixedThreadPool(150);

        Deque<String> seeds = allCombos(input).collect(Collectors.toCollection(ArrayDeque::new));
        List<SeedInfo> seedData = new ArrayList<>(seeds.size());

        AtomicInteger cur = new AtomicInteger();

        String seed;
        while ((seed = seeds.poll()) != null) {
            int andIncrement = cur.getAndIncrement();
            if (andIncrement % 100 == 0) {
                System.out.println("on " + andIncrement);
            }
            String finalSeed = seed;
            processor.execute(() -> seedData.add(new SeedInfo(finalSeed)));
        }

        System.out.println("Awaiting Thread Pool Termination...");
        processor.shutdown();
        try {
            //noinspection ResultOfMethodCallIgnored
            processor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            processor.shutdownNow();
            e.printStackTrace();
        }

        seedData.sort(Comparator.comparingDouble(info -> info.score));
        seedData.forEach(info -> {
            try {
                fileOutput.write(a.getAndIncrement() + ": " + info.seed + " name: " + info.seedName + "\n");
                fileOutput.write("score: " + info.score + "bastion: " + info.bastionMag + " fortress: " + info.fortressDist + " stronghold: " + info.strongholdDist + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });



    }

    public static class SeedInfo {
        public final long seed;
        public final String seedName;
        public final double score;
        private static final BastionRemnant bastionRemnant = new BastionRemnant(v1_16_1);
        private static final Fortress fortress = new Fortress(v1_16_1);
        private static final Stronghold stronghold = new Stronghold(v1_16_1);

        private static long sum = 0;

        public final double bastionMag;
        public final double fortressDist;
        public final double strongholdDist;
        private static final ChunkRand rand = new ChunkRand();

        public SeedInfo(long seed) {
            this(seed, null);
        }

        public SeedInfo(String seedName) {
            this(seedName.hashCode(), seedName);
        }

        private SeedInfo(long seed, String seedName) {
            this.seed = seed;
            this.seedName = seedName;
            sum++;
            if (sum % 100 == 0) {
                System.out.println("Done with " + sum);
            }
            CPos[] bastionLocs = new CPos[16];
            CPos[] fortressLocs = new CPos[16];
            NetherBiomeSource biomeSource = new NetherBiomeSource(v1_16_1, seed);
            for (int x = -2; x < 2; x++) {
                for (int z = -2; z < 2; z++) {
                    CPos bastion = bastionRemnant.getInRegion(seed, x, z, rand);
                    if (bastion != null) {
                        if (bastionRemnant.canSpawn(bastion, biomeSource)) {
                            bastionLocs[(x + 2) + (z + 2) * 4] = bastion;
                        }
                    } else {
                        fortressLocs[(x + 2) + (z + 2) * 4] = fortress.getInRegion(seed, x, z, rand);
                    }
                }
            }
            List<CPos> bastions = Arrays.stream(bastionLocs).filter(Objects::nonNull)
                    .sorted(Comparator.comparingDouble(CPos::getMagnitudeSq))
                    .limit(2)
                    .toList();
            if (bastions.isEmpty()) {
                strongholdDist = fortressDist = bastionMag = score = Double.MAX_VALUE;
                return;
            }
            Optional<CPos> bestFort_ = Arrays.stream(fortressLocs).filter(Objects::nonNull)
                    .min(Comparator.comparingDouble(fortress -> bastions.stream()
                            .mapToDouble(bastion -> fortress.distanceTo(bastion, DistanceMetric.EUCLIDEAN_SQ)).min()
                            .orElse(Double.MAX_VALUE)));
            if (bestFort_.isEmpty()) {
                strongholdDist = fortressDist = bastionMag = score = Double.MAX_VALUE;
                return;
            }
            CPos bestFort = bestFort_.get();
            Optional<CPos> bestStronghold_ = Arrays.stream(stronghold.getStarts(BiomeSource.of(Dimension.OVERWORLD, v1_16_1, seed), 10, JRand.ofInternalSeed(seed)))
                    .min(Comparator.comparingDouble(stronghold -> stronghold.distanceTo(bestFort, DistanceMetric.EUCLIDEAN_SQ)));
            if (bestStronghold_.isEmpty()) {
                strongholdDist = fortressDist = bastionMag = score = Double.MAX_VALUE;
                return;
            }
            this.bastionMag = bastions.get(0).getMagnitude() * 16;
            this.fortressDist = bestFort.distanceTo(bastions.get(0), DistanceMetric.EUCLIDEAN) * 16;
            this.strongholdDist = bestStronghold_.get().shr(3).distanceTo(bestFort, DistanceMetric.EUCLIDEAN) * 16;
            score = bastionMag * 16 + fortressDist + strongholdDist;


            // quadruple bastion distance and then add the fortress + stronghold dist

        }
    }


    private static Stream<String> allCombos(String s) {
        if (s.isEmpty()) {
            return Stream.of(s);
        } else {
            char c = s.charAt(0);
            char u = Character.toUpperCase(c);
            Stream<String> combos = allCombos(s.substring(1));
            if (c == u) {
                return combos.map(str -> c + str);
            } else {
                String[] strings = combos.toArray(String[]::new);
                return Stream.concat(Stream.of(strings).map(str -> c + str), Stream.of(strings).map(str -> u + str));
            }
        }
    }
}
