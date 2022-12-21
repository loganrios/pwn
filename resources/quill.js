var quill = new Quill('#editor', {
  theme: 'snow',
  placeholder: "Start writing here..."
});

var form = document.getElementById("chapter-content-form");
form.onsubmit = function() {
  // Populate hidden form on submit
  var about = document.querySelector('input[name=chapter-content]');
  about.value = quill.root.innerHTML;
  return true;
};
