with-weightsdefrecord Graph [vertices edges])
(defrecord Edge [from to weight label])
(defrecord Vertex [label neighbors latitude longitude status distance])
(defrecord SListNode [next data priority])
(defrecord SList [head])


(def ^:const vertex-status-unseen 0)
(def ^:const vertex-status-in-queue 1)
(def ^:const vertex-status-visited 2)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; PRIORITY QUEUE ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn slist-make[]
  (SList. (ref nil)))

(defn slist? [lst]
  (= (class lst) SList))

(defn slist-empty? [lst]
  (nil? @(:head lst)))

(defn slist-first [lst]
  (:data @(:head lst)))

(defn slist-prepend! [lst val priority]
  (dosync
    (ref-set (:head lst)
             (SListNode. (ref (deref (:head lst))) val priority))))

(defn slist-insert-priority! [lst val priority]
  (if-not (nil? @(:head lst))
    (let [tracer @(:head lst)
          flag (ref false)]
      (if (<= priority (:priority tracer))
        (do
          (slist-prepend! lst val priority) true)
        (if-not (nil? @(:next tracer))
          (loop [next-node @(:next tracer)
                 tracer tracer]
            (if (<= priority (:priority next-node))
              (dosync
                (ref-set (:next tracer)
                         (SListNode. (ref next-node) val priority))
                (ref-set flag true))
              (if (and (not @flag) (nil? @(:next next-node)))
                (dosync
                  (ref-set (:next next-node)
                           (SListNode. (ref nil) val priority)) true)
                (recur @(:next next-node) @(:next tracer)))))
          (dosync
            (ref-set (:next tracer)
                     (SListNode. (ref nil) val priority)) true))))
    (dosync
      (ref-set (:head lst)
               (SListNode. (ref (deref (:head lst))) val priority)))))

(defn slist-pop-first! [lst]
  (dosync
    (ref-set (:head lst) @(:next @(:head lst)))) true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;; INITIALIZING THE GRAPH ;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn make-graph [] (Graph. (ref {}) (ref {})))

(defn graph-has-vertex? [graph label]
  (contains? @(:vertices graph) label))

(defn graph-add-vertex! [graph label lat lon]
  (if-not (graph-has-vertex? graph label)
    (dosync
      (ref-set (:vertices graph)
               (assoc @(:vertices graph)
                      label
                      (Vertex. label (ref '()) lat lon
                               (ref vertex-status-unseen)
                               (ref 0)))) true)
    (do
      (println "Vertex already in the graph") false)))



(defn graph-make-edge-key [from to] (sort (list from to)))

(defn graph-has-edge? [graph from to]
  (contains? @(:edges graph) (graph-make-edge-key from to)))

(defn graph-add-edge! [graph from to label weight]
  (if (and (graph-has-vertex? graph from) (graph-has-vertex? graph to))
    (if-not (graph-has-edge? graph from to)
      (dosync
        (ref-set (:edges graph)
                 (assoc @(:edges graph)
                        (graph-make-edge-key from to)
                        (Edge. from to weight label)))
        (let [neighbors (:neighbors (get @(:vertices graph) from))]
          (ref-set neighbors (cons to @neighbors)))
        (let [neighbors (:neighbors (get @(:vertices graph) to))]
          (ref-set neighbors (cons from @neighbors))) true)
      (do
        (println "Edge already in the graph") false))
    (do (println "Invalid vertices") false)))

(defn vertex-reset-status! [vertex]
  (dosync
    (ref-set (:status vertex) vertex-status-unseen)))

(defn vertex-reset-distance! [vertex]
  (dosync
    (ref-set (:distance vertex) 0)))

(defn vertex-reset-all! [vertex]
  (vertex-reset-status! vertex)
  (vertex-reset-distance! vertex))

(defn graph-reset!
  ([graph]
   (graph-reset! graph vertex-reset-all!))
  ([graph reset-function]
   (doseq [vertex (vals @(:vertices graph))]
     (reset-function vertex))))

(defn graph-vertex-status? [graph label status]
  (= @(:status (get @(:vertices graph) label))
     status))

(defn graph-vertex-visited? [graph label]
  (graph-vertex-status? graph label vertex-status-visited))

(defn graph-vertex-unseen? [graph label]
  (graph-vertex-status? graph label vertex-status-unseen))

(defn graph-vertex-unseen-or-in-queue? [graph label]
  (or (graph-vertex-status? graph label vertex-status-unseen)
      (graph-vertex-status? graph label vertex-status-in-queue)))

(defn graph-get-vertex [graph label]
  (get @(:vertices graph) label))

(defn graph-get-edge [graph from to]
  (get @(:edges graph) (graph-make-edge-key from to)))

(defn graph-get-edge-weight
  ([e]
   (:weight e))
  ([graph from to]
   (graph-get-edge-weight (graph-get-edge graph from to))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;; DIJKSTRA'S ALGORITHM ;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn graph-bfs!
  ([graph]
   (graph-bfs! graph (first (keys @(:vertices graph)))))
  ([graph start]
   (graph-bfs! graph start (fn [x] true)))
  ([graph start func]
   (graph-bfs! graph start func (fn [graph queue] (first queue))))
  ([graph start func queue]
   (loop []
     (when-not (slist-empty? queue)
       (let [current-label (slist-first queue)
             current-vertex (get @(:vertices graph) current-label)
             current-neighbors @(:neighbors current-vertex)
             unseen-neighbors (filter #(graph-vertex-unseen? graph %1)
                                      current-neighbors)]
         (let [continue? (func current-vertex queue)]
           (dosync (ref-set (:status current-vertex) vertex-status-visited))
           (if continue?
             (recur) (println "Marking stage done"))))))))

(defn vertex-get-best-neighbor [graph vertex]
  (let [best-distance (ref ##Inf)
        best-label (ref "")]
    (doseq [neighbor-label @(:neighbors vertex)]
      (let [neighbor (graph-get-vertex graph neighbor-label)]
        (if (and (= @(:status neighbor) vertex-status-visited)
                 (< @(:distance neighbor) @best-distance))
          (dosync
            (ref-set best-distance @(:distance neighbor))
            (ref-set best-label (:label neighbor))))))
    @best-label))

(defn vertex-get-best-neighbor-with-weights [graph vertex]
  (let [best-distance (ref ##Inf)
        best-label (ref "")]
    (doseq [neighbor-label @(:neighbors vertex)]
      (let [neighbor (graph-get-vertex graph neighbor-label)]
        (when (and (= @(:status neighbor) vertex-status-visited)
                   (< @(:distance neighbor) @best-distance)
                   (= (- @(:distance vertex) @(:distance neighbor))
                      (graph-get-edge-weight graph
                                             (:label vertex)
                                             (:label neighbor))))
          (dosync
            (ref-set best-distance @(:distance neighbor))
            (ref-set best-label (:label neighbor))))))
    @best-label))

(defn graph-trace-back [graph start finish best-neighbor-func]
  (let [start-vertex (graph-get-vertex graph start)
        ret-lst (ref '())]
    (if (= @(:status start-vertex) vertex-status-visited)
      (loop [current-label start]
        (if (not (= current-label finish))
          (let [current-vertex (graph-get-vertex graph current-label)]
            (println ">>" current-label "::" @(:distance current-vertex))
            (dosync (ref-set ret-lst
                             (conj @ret-lst current-label))
                    (ref-set (:status current-vertex) vertex-status-unseen))
            (recur (best-neighbor-func graph current-vertex)))
          (do
            (println "**" current-label)
            (println "Arrived at finish!")
            )))
      (do
        (newline)
        (println "Path does not exist!"))) (reverse @ret-lst)))

(defn graph-dijkstra-helper! [graph start finish]
  (graph-reset! graph)
  (dosync
    (ref-set (:distance (graph-get-vertex graph finish)) 0))
  (let [queue (slist-make)
        cnt (ref 0)]
    (slist-insert-priority! queue finish @(:distance (graph-get-vertex graph finish)))
    (graph-bfs! graph
                finish
                (fn [vertex queue]
                  (slist-pop-first! queue)
                  (if (= start (:label vertex))
                    false
                    (if-not (= @(:status vertex) vertex-status-visited)
                      (do
                        (dosync (ref-set cnt (inc @cnt)))
                        (doseq [neighbor-label
                                (filter (fn [label]
                                          (graph-vertex-unseen? graph label))
                                        @(:neighbors vertex))]
                          (let [neighbor (graph-get-vertex graph neighbor-label)]
                            (dosync
                              (ref-set (:distance neighbor)
                                       (inc @(:distance vertex)))
                              (ref-set (:status neighbor) vertex-status-in-queue))
                            (slist-insert-priority! queue neighbor-label @(:distance neighbor))))
                        true)))) queue)
    (println "Vertices visited:" @cnt)
    (newline)
    (graph-trace-back graph start finish vertex-get-best-neighbor)))
(defn graph-a*-helper! [graph start finish]
  (graph-reset! graph)
  (dosync
   (ref-set (:distance (graph-get-vertex graph finish)) 0))
  (let [queue (slist-make)
        cnt (ref 0)]
    (slist-insert-priority! queue finish @(:distance (graph-get-vertex graph finish)))
  (graph-bfs! graph
              finish
              (fn [vertex queue]
                (slist-pop-first! queue)
                (if (= start (:label vertex))
                    false
                (if-not (= @(:status vertex) vertex-status-visited)
                  (do
                  (dosync (ref-set cnt (inc @cnt)))
                (if (= start (:label vertex))
                    false
                  (do
                    (doseq [neighbor-label
                            (filter
                             (fn [label]
                               (graph-vertex-unseen-or-in-queue? graph label))
                             @(:neighbors vertex))]
                      (let [neighbor (graph-get-vertex graph neighbor-label)
                            weight (graph-get-edge-weight graph
                                                          (:label vertex)
                                                          neighbor-label)
                            distance (+ @(:distance vertex)
                                        weight (graph-great-circle-distance graph (:label vertex) start))]
                        (println distance)
                         (dosync (ref-set (:status neighbor) vertex-status-in-queue))
                        (when (or (= @(:distance neighbor) 0)
                                  (< distance @(:distance neighbor)))
                          (dosync
                           (ref-set (:distance neighbor)
                                    distance)))
                      (slist-insert-priority! queue neighbor-label @(:distance neighbor))))
                    true))) true)))
              queue)
  (println "Vertices visited:" @cnt)
  (newline)
  (graph-trace-back graph start finish
                             vertex-get-best-neighbor-with-weights)
  ))



(defn graph-dijkstra-with-weights-helper! [graph start finish]
  (graph-reset! graph)
  (dosync
    (ref-set (:distance (graph-get-vertex graph finish)) 0))
  (let [queue (slist-make)
        cnt (ref 0)]
    (slist-insert-priority! queue finish @(:distance (graph-get-vertex graph finish)))
    (graph-bfs! graph
                finish
                (fn [vertex queue]
                  (slist-pop-first! queue)
                  (if (= start (:label vertex))
                    false
                    (if-not (= @(:status vertex) vertex-status-visited)
                      (do
                        (dosync (ref-set cnt (inc @cnt)))
                        (if (= start (:label vertex))
                          false
                          (do
                            (doseq [neighbor-label
                                    (filter
                                      (fn [label]
                                        (graph-vertex-unseen-or-in-queue? graph label))
                                      @(:neighbors vertex))]
                              (let [neighbor (graph-get-vertex graph neighbor-label)
                                    weight (graph-get-edge-weight graph
                                                                  (:label vertex)
                                                                  neighbor-label)
                                    distance (+ @(:distance vertex)
                                                weight)]
                                (dosync (ref-set (:status neighbor) vertex-status-in-queue))
                                (when (or (= @(:distance neighbor) 0)
                                          (< distance @(:distance neighbor)))
                                  (dosync
                                    (ref-set (:distance neighbor)
                                             distance)))
                                (slist-insert-priority! queue neighbor-label @(:distance neighbor))))
                            true))) true)))
                queue)
    (println "Vertices visited:" @cnt)
    (newline)
    (graph-trace-back graph start finish
                      vertex-get-best-neighbor-with-weights)
    ))

(defn format-lst [g lst finish]
  (newline)(newline)
  (println "------------------------------------------")
  (println "---------------INSTRUCTIONS---------------")
  (println "------------------------------------------")
  (newline)(newline)
  (let [string (ref '())]
    (dosync
      (ref-set string (str "Start at " (first lst) " and "))
      (loop [x (rest lst)]
        (if (> (count x) 0)
          (do
            (let [f (first x)
                  fr (first (rest x))
                  edge (:label (graph-get-edge g f fr))]
              (if (= (subs (first x) 0 1) "X")
                (do
                  (ref-set string (str @string "take " edge " on the "
                                       (subs f 3 12) " highway crossing. Next, you "))
                  (ref-set string (str @string "choose " edge " in the "
                                       (subs f 3 12) " highway crossing. After passing it, ")))
                (if (even? (count x))
                  (if (even? (rand-int 10))
                    (ref-set string (str @string "via " edge " drive to " f
                                         ". After getting there, "))
                    (ref-set string (str @string "through " edge " continue to " f
                                         ". From " f ", ")))
                  (if (even? (rand-int 10))
                    (ref-set string (str @string "take " edge " and drive to " f
                                         ". Then, "))
                    (ref-set string (str @string "via " edge " continue to " f
                                         ". When you get to " f ", "))))))
            (recur (rest x)))
          (ref-set string (str @string "take the road to " finish
                               " and you will arrive at the finish!")))))
    (println @string)))

(defn graph-dijkstra! [graph start finish]
  (if (and (graph-has-vertex? graph start)
           (graph-has-vertex? graph finish))
    (let [lst (graph-dijkstra-helper! graph start finish)]
      (if-not (empty? lst)
        (format-lst graph lst finish)
        nil))
    "Invalid vertices"))

(defn graph-dijkstra-with-weights! [graph start finish]
  (if (and (graph-has-vertex? graph start)
           (graph-has-vertex? graph finish))
    (let [lst (graph-dijkstra-with-weights-helper! graph start finish)]
      (if-not (empty? lst)
        (format-lst graph lst finish)
        nil))
    "Invalid vertices"))

(defn graph-a*! [graph start finish]
  (if (and (graph-has-vertex? graph start)
           (graph-has-vertex? graph finish))
    (let [lst (graph-a*-helper! graph start finish)]
      (if-not (empty? lst)
        (format-lst graph lst finish)
        nil))
    "Invalid vertices"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; A* SEARCH ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn graph-bfs-remove-from-queue [queue label]
  (filter (fn [x]
            (not (= x label)))
          queue))

(defn graph-great-circle-distance [graph label1 label2]
  (let [vertex1 (graph-get-vertex graph label1)
        vertex2 (graph-get-vertex graph label2)
        lat1 (:latitude vertex1)
        lon1 (:longitude vertex1)
        lat2 (:latitude vertex2)
        lon2 (:longitude vertex2)
        dl (Math/abs (- lon2 lon1)) ; lambda - longitude
        dp (Math/abs (- lat2 lat1)) ; phi - latitude
        dlr (/ (* Math/PI dl) 180)
        dpr (/ (* Math/PI dp) 180)
        l1 (/ (* Math/PI lon1) 180)
        p1 (/ (* Math/PI lat1) 180)
        l2 (/ (* Math/PI lon2) 180)
        p2 (/ (* Math/PI lat2) 180)
        ds (Math/acos (+ (* (Math/sin p1) (Math/sin p2))
                         (* (Math/cos p1) (Math/cos p2) (Math/cos dlr))))]
    (* 6378 ds)))

(defn graph-a-star-pick-best [graph queue finish]
  (let [best-distance (ref ##Inf)
        best-label (ref "")]
    (doseq [label queue]
      (let [vertex (graph-get-vertex graph label)
            distance-to-finish (graph-great-circle-distance graph
                                                            label
                                                            finish)
            cost-estimation (+ @(:distance vertex) distance-to-finish)]
        (if (< cost-estimation @best-distance)
          (dosync
            (ref-set best-distance cost-estimation)
            (ref-set best-label (:label vertex))))))
    @best-label))

(defn graph-best-first-search!
  ([graph]
   (graph-best-first-search! graph (first (keys @(:vertices graph)))))
  ([graph start]
   (graph-best-first-search! graph start (fn [x] true)))
  ([graph start func]
   (graph-best-first-search! graph start func (fn [graph queue] (first queue))))
  ([graph start func pick-best]
   (loop [queue (list start)]
     (when-not (empty? queue)
       (let [current-label (pick-best graph queue)
             current-vertex (get @(:vertices graph) current-label)
             current-neighbors @(:neighbors current-vertex)
             unseen-neighbors (filter #(graph-vertex-unseen? graph %1)
                                      current-neighbors)]
         (let [continue? (func current-vertex)]
           (dosync (ref-set (:status current-vertex) vertex-status-visited))
           (dosync
             (doseq [neighbor unseen-neighbors]
               (ref-set (:status (get @(:vertices graph) neighbor))
                        vertex-status-in-queue)))
           (if continue?
             (recur (concat (graph-bfs-remove-from-queue queue current-label)
                            unseen-neighbors)))))))))

(defn graph-a-star-helper! [graph finish start]
  (graph-reset! graph)
  (dosync
    (ref-set (:distance (graph-get-vertex graph start)) 0))
  (let [cnt (ref 0)]
    (graph-best-first-search! graph
                              start
                              (fn [vertex]
                                (dosync (ref-set cnt (inc @cnt)))
                                (if (= finish (:label vertex))
                                  false
                                  (do
                                    (doseq [neighbor-label
                                            (filter
                                              (fn [label]
                                                (graph-vertex-unseen-or-in-queue? graph label))
                                              @(:neighbors vertex))]
                                      (let [neighbor (graph-get-vertex graph neighbor-label)
                                            weight (graph-get-edge-weight graph
                                                                          (:label vertex)
                                                                          neighbor-label)
                                            distance (+ @(:distance vertex)
                                                        weight)]
                                        (when (or (= @(:distance neighbor) 0)
                                                  (< distance @(:distance neighbor)))
                                          (dosync
                                            (ref-set (:distance neighbor)
                                                     distance)))))
                                    true)))
                              (fn [graph queue]
                                (graph-a-star-pick-best graph queue finish)))
    (println "Vertices visited:" @cnt)
    (newline))
  (graph-trace-back graph finish start
                    vertex-get-best-neighbor-with-weights))

(defn graph-a-star! [graph start finish]
  (if (and (graph-has-vertex? graph start)
           (graph-has-vertex? graph finish))
    (let [lst (graph-a-star-helper! graph start finish)]
      (if-not (empty? lst)
        (format-lst graph lst finish)
        nil))
    "Invalid vertices"))
