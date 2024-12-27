(ns ollama.models
  (:require [cljfx.api :as fx]
            [pyjama.core]
            [pyjama.models :refer :all]
            [pyjama.state]))

(def state
  (atom {:query ""
         :url "http://localhost:11434"
         :models []
         :local-models []}))

(defn table-view [{:keys [models sort-key sort-direction query local-models]}]
  {:fx/type :v-box
   :children [{:fx/type :h-box
               :alignment :center-left
               :spacing 10
               :children [{:fx/type :label
                           :text "Search:"}
                          {:fx/type :text-field
                           :text query
                           :on-text-changed #(swap! state assoc :query %)}]}
              {:fx/type :table-view
               :items (-> models (filter-models query))
               :columns [{:fx/type :table-column
                          :text "Name"
                          :cell-value-factory :name}
                         {:fx/type :table-column
                          :text "Description"
                          :max-width 500
                          :cell-value-factory :description}
                         {:fx/type :table-column
                          :text "Updated"
                          :cell-value-factory :updated}
                         {:fx/type :table-column
                          :text "Pulls"
                          :cell-value-factory :pulls}
                         ;; New column for marking remote models
                         {:fx/type :table-column
                          :text "Remote"
                          :cell-value-factory (fn [model]
                                                (if (some #(= (:name model) %) local-models)
                                                  "☑️"
                                                  ""))}]}]})

;; The main application view
(defn app-view [state]
  {:fx/type :stage
   :showing true
   :title "Ollama Models"
   :width 1200
   :height 700
   :scene   {:fx/type      :scene
             :stylesheets  #{"styles.css"}
             :accelerators {[:escape] {:event/type ::close}}
             :root         {:fx/type  :v-box
                            :spacing  10
                            :padding  10
                            :children [(table-view state)]}}})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type app-view)
    :opts {:state state}))

(defn -main []
  (pyjama.state/local-models state)
  (pyjama.state/remote-models state)
  (fx/mount-renderer state renderer))
