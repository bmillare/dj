;; To install auto-complete, download from git://github.com/m2ym/auto-complete.git
;; Then M-x load-file, auto-complete/etc/install.el
;; Make the folder dj/lib/ac-dict
;; Make the file dj/lib/ac-dict/clojure-mode

(require 'cl)
(add-to-list 'load-path "~/.emacs.d/")
(require 'auto-complete-config)
(add-to-list 'ac-dictionary-directories "~/.emacs.d//ac-dict")
(ac-config-default)

(defun dj-toolkit-completions ()
  (comint-send-string (inferior-lisp-proc)
		      "(dj.toolkit/poop (dj.toolkit/new-file (System/getProperty \"user.home\") \"dj/lib/ac-dict/clojure-mode\") (apply str (interpose \"\n\" (dj.toolkit/all-completions *ns*))))\n"))

(defun ac-source-dj-toolkit-candidates ()
  "Return a possibly-empty list of completions for the symbol at point."
  (if (inferior-lisp-proc)
      (dj-toolkit-complete-sexp ac-prefix)))

(defvar ac-dj-toolkit-current-doc nil "Holds dj-toolkit docstring for current symbol")

(defun dj-toolkit-parse (proc text)
  (setq ac-dj-toolkit-current-doc text)
  text)

(defun dj-toolkit-doc (s)
  (let ((proc (inferior-lisp-proc)))
    (process-send-string (inferior-lisp-proc)
			 (format "(clojure.repl/doc %s)\n"
				 s))
    (set-process-filter proc 'dj-toolkit-parse)
    (accept-process-output proc 1)
    (set-process-filter proc nil)
    ac-dj-toolkit-current-doc))

(defvar cache-is-new '("true"))

(defun dummy-candidates ()
  (or cache-is-new
      (progn
	(dj-toolkit-completions)
	(ac-clear-dictionary-cache)
	(setq cache-is-new '("true")))))

(defvar dummy-source
  '((candidates . dummy-candidates)))

(defun update-dj-cache ()
  (if (and (equal major-mode 'clojure-mode)
	   auto-complete-mode)
      (progn
	(dj-toolkit-completions)
	(ac-clear-dictionary-cache)
	(setq cache-is-new '("true")))
    nil))

(defun set-up-dj-toolkit-ac ()
  "Add dj-toolkit completion source to the
front of `ac-sources' for the current buffer."
  (interactive)
  (add-to-list 'ac-dictionary-directories "~/dj/lib/ac-dict/")
  (setq ac-dj-toolkit-current-doc nil)
  (ac-clear-dictionary-cache)
  ;; (add-hook 'after-save-hook 'update-dj-cache)
  (setq ac-sources (add-to-list 'ac-sources 'dummy-source))
  (setq ac-source-dictionary
	'((candidates . ac-buffer-dictionary)
	  (symbol . "d")
	  (document . dj-toolkit-doc))))

(setq inferior-lisp-program "dj repl")

(require 'clojure-mode)
(add-hook 'clojure-mode-hook 'set-up-dj-toolkit-ac)
(add-hook 'clojure-mode-hook (lambda () (paredit-mode +1)))
(require 'paredit)
(define-key paredit-mode-map "{" 'paredit-open-brace)
(define-key paredit-mode-map "}" 'paredit-close-brace)
(define-key paredit-mode-map ")" 'paredit-close-parenthesis)
(define-key paredit-mode-map "]" 'paredit-close-bracket)
(define-key paredit-mode-map (kbd "C-M-a") 'backward-up-list)
(define-key paredit-mode-map (kbd "C-M-e") 'up-list)
(defun clojure-load-file (file-name)
  "Load a Lisp file into the inferior Lisp process."
  (interactive (comint-get-source "Load Clojure file: "
                                  clojure-prev-l/c-dir/file
                                  '(clojure-mode) t))
  (comint-check-source file-name) ; Check to see if buffer needs saved.
  (setq clojure-prev-l/c-dir/file (cons (file-name-directory file-name)
                                        (file-name-nondirectory file-name)))
  (comint-send-string (inferior-lisp-proc)
                      (format clojure-mode-load-command file-name))
  (update-dj-cache))

(defun doc-sexp (&optional and-go)
  (interactive "P")
  (comint-send-string (inferior-lisp-proc)
		      (format "(dj.toolkit/text-box \"doc\" (with-out-str (clojure.repl/doc %s)))\n"
			      (save-excursion
				(backward-sexp)
				(forward-sexp)
				(preceding-sexp)))))
(global-set-key "\C-cd" 'doc-sexp)
(defun src-sexp (&optional and-go)
  (interactive "P")
  (comint-send-string (inferior-lisp-proc)
		      (format "(dj.toolkit/text-box \"source\" (with-out-str (clojure.repl/source %s)))\n"
			      (save-excursion
				(backward-sexp)
				(forward-sexp)
				(preceding-sexp)))))
(global-set-key "\C-cs" 'src-sexp)
(defun javadoc-sexp (&optional and-go)
  (interactive "P")
  (comint-send-string (inferior-lisp-proc)
		      (format "(clojure.java.javadoc/javadoc %s)\n"
			      (save-excursion
				(backward-sexp)
				(forward-sexp)
				(preceding-sexp)))))
(global-set-key "\C-cj" 'javadoc-sexp)
(defun apropos-sexp (&optional and-go)
  (interactive "P")
  (comint-send-string (inferior-lisp-proc)
		      (format "(dj.toolkit/sequence-box \"var-apropos\" (dj.toolkit/var-apropos \"%s\" *ns*))\n"
			      (preceding-sexp))))
(global-set-key "\C-ca" 'apropos-sexp)
(defun apropos-all-sexp (&optional and-go)
  (interactive "P")
  (comint-send-string (inferior-lisp-proc)
		      (format "(dj.toolkit/sequence-box \"var-apropos-all\" (dj.toolkit/var-apropos \"%s\" :all))\n"
			      (preceding-sexp))))
(global-set-key "\C-cA" 'apropos-all-sexp)
(global-set-key "\C-ce" 'eval-print-last-sexp)

(provide 'dj-toolkit)

